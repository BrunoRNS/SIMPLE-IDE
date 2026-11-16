package io.simpleide.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates a usable Java runtime for launching helper processes.
 *
 * <p>
 * A development launch runs on a full JDK, so {@code java.home/bin/java} is enough. A
 * jpackage image used to strip the native launchers from its runtime; this helper
 * therefore also accepts an explicit home, {@code JAVA_HOME}, the current process command
 * and {@code PATH}, and never assumes that {@code java.home} contains a launcher.
 * Packaging now keeps the launchers with
 * {@code --jlink-options "--strip-debug --no-man-pages --no-header-files"}, but the
 * fallbacks keep older images and custom runtimes working.
 *
 * <p>
 * The embedded bytecode compiler and the Java language server both need the
 * {@code jdk.compiler} module, so {@link #jdkHome()} and {@link #jdkJavaExecutable()}
 * only accept a runtime that provides it.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class JavaRuntime {

	/**
	 * System property that overrides the Java home used for helper processes.
	 */
	public static final String JAVA_HOME_PROPERTY = "simpleide.javaHome";

	/**
	 * System property kept for compatibility with the language server settings.
	 */
	public static final String LSP_JAVA_HOME_PROPERTY = "simpleide.lsp.javaHome";

	private static final String[] HOME_PROPERTIES = { JAVA_HOME_PROPERTY, LSP_JAVA_HOME_PROPERTY };

	private static final String[] HOME_ENVIRONMENT = { "SIMPLEIDE_JAVA_HOME", "JAVA_HOME", "JDK_HOME" };

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	private static final String JAVA_EXECUTABLE = WINDOWS ? "java.exe" : "java";

	private static final String JAVAC_EXECUTABLE = WINDOWS ? "javac.exe" : "javac";

	private static final Pattern MODULES_PATTERN = Pattern.compile("MODULES=\"([^\"]*)\"");

	private JavaRuntime() {
	}

	/**
	 * Returns the Java launcher to use for helper processes.
	 *
	 * <p>
	 * A runtime with {@code jdk.compiler} is preferred. A plain JRE is used only as a
	 * last resort, when no JDK can be found.
	 * @return the launcher path, or an empty optional when no Java runtime is available
	 */
	public static Optional<Path> javaExecutable() {
		Optional<Path> jdk = jdkJavaExecutable();
		if (jdk.isPresent()) {
			return jdk;
		}
		return candidateHomes().stream().map(JavaRuntime::javaExecutable).flatMap(Optional::stream).findFirst();
	}

	/**
	 * Returns the home of the first usable JDK.
	 * @return the JDK home, or an empty optional when no JDK is available
	 */
	public static Optional<Path> jdkHome() {
		return firstJdk(candidateHomes());
	}

	/**
	 * Returns the Java launcher from a usable JDK.
	 * @return the JDK launcher path, or an empty optional when no JDK is available
	 */
	public static Optional<Path> jdkJavaExecutable() {
		return jdkHome().map(home -> home.resolve("bin").resolve(JAVA_EXECUTABLE));
	}

	/**
	 * Returns the {@code javac} launcher from a usable JDK.
	 * @return the compiler path, or an empty optional when no JDK with the launcher is
	 * available
	 */
	public static Optional<Path> jdkJavacExecutable() {
		return jdkHome().map(home -> home.resolve("bin").resolve(JAVAC_EXECUTABLE))
			.filter(JavaRuntime::isExecutableFile);
	}

	/**
	 * Returns the environment overrides needed by Java-based helper processes.
	 * @return a map containing {@code JAVA_HOME} when a JDK is available, otherwise an
	 * empty map
	 */
	public static Map<String, String> environment() {
		Optional<Path> home = jdkHome();
		if (home.isEmpty()) {
			return Map.of();
		}
		Map<String, String> environment = new LinkedHashMap<>();
		environment.put("JAVA_HOME", home.get().toString());
		return environment;
	}

	/**
	 * Tells whether a configured command refers to the bare {@code java} launcher.
	 * @param command command name or path
	 * @return {@code true} for {@code java} and {@code java.exe}
	 */
	public static boolean isJavaCommand(String command) {
		if (command == null) {
			return false;
		}
		String normalized = command.strip().toLowerCase(Locale.ROOT);
		return "java".equals(normalized) || "java.exe".equals(normalized);
	}

	/**
	 * Tells whether a directory contains a Java launcher and the {@code jdk.compiler}
	 * module.
	 * @param home candidate Java home
	 * @return {@code true} when the directory is a usable JDK
	 */
	static boolean isJdkHome(Path home) {
		if (!javaExecutable(home).isPresent()) {
			return false;
		}
		if (isExecutableFile(home.resolve("bin").resolve(JAVAC_EXECUTABLE))) {
			return true;
		}
		return hasCompilerModule(home);
	}

	static Optional<Path> javaExecutable(Path home) {
		if (home == null) {
			return Optional.empty();
		}
		Path executable = home.resolve("bin").resolve(JAVA_EXECUTABLE);
		return isExecutableFile(executable) ? Optional.of(executable.toAbsolutePath().normalize()) : Optional.empty();
	}

	static Optional<Path> firstJdk(List<Path> homes) {
		return homes.stream().filter(JavaRuntime::isJdkHome).findFirst();
	}

	static List<Path> candidateHomes() {
		Set<Path> candidates = new LinkedHashSet<>();
		for (String property : HOME_PROPERTIES) {
			addHome(candidates, System.getProperty(property));
		}
		for (String variable : HOME_ENVIRONMENT) {
			addHome(candidates, System.getenv(variable));
		}

		ProcessHandle.current().info().command().ifPresent(command -> {
			try {
				Path executable = Path.of(command);
				if (isJavaExecutableName(executable.getFileName())) {
					Path bin = executable.getParent();
					if (bin != null && bin.getParent() != null) {
						addHome(candidates, bin.getParent().toString());
					}
				}
			}
			catch (InvalidPathException ignored) {
				// Ignore unusable platform command paths.
			}
		});

		addHome(candidates, System.getProperty("java.home"));

		String pathEnvironment = System.getenv("PATH");
		if (pathEnvironment != null) {
			for (String entry : pathEnvironment.split(File.pathSeparator)) {
				if (entry.isBlank()) {
					continue;
				}
				try {
					Path directory = Path.of(entry);
					Path name = directory.getFileName();
					if (name != null && "bin".equalsIgnoreCase(name.toString()) && directory.getParent() != null) {
						addHome(candidates, directory.getParent().toString());
					}
					else {
						addHome(candidates, entry);
					}
				}
				catch (InvalidPathException ignored) {
					// Ignore malformed PATH entries.
				}
			}
		}

		return new ArrayList<>(candidates);
	}

	private static void addHome(Set<Path> candidates, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		String expanded = value.strip();
		if (expanded.startsWith("~/") || expanded.startsWith("~\\")) {
			expanded = System.getProperty("user.home") + expanded.substring(1);
		}
		try {
			candidates.add(Path.of(expanded).toAbsolutePath().normalize());
		}
		catch (InvalidPathException ignored) {
			// Ignore malformed configuration values.
		}
	}

	private static boolean isJavaExecutableName(Path fileName) {
		return fileName != null && JAVA_EXECUTABLE.equalsIgnoreCase(fileName.toString());
	}

	private static boolean isExecutableFile(Path path) {
		return Files.isRegularFile(path) && Files.isExecutable(path);
	}

	private static boolean hasCompilerModule(Path home) {
		Path release = home.resolve("release");
		if (!Files.isRegularFile(release)) {
			return false;
		}
		try {
			Matcher matcher = MODULES_PATTERN.matcher(Files.readString(release));
			if (!matcher.find()) {
				return false;
			}
			for (String module : matcher.group(1).split("\\s+")) {
				if ("jdk.compiler".equals(module)) {
					return true;
				}
			}
		}
		catch (IOException ignored) {
			// Fall through to the missing-compiler result.
		}
		return false;
	}

}
