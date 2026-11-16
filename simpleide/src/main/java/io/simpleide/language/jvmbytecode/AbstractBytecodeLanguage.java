package io.simpleide.language.jvmbytecode;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.utils.JavaRuntime;
import io.simpleide.utils.ResourceUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for JVM languages executed through the embedded bytecode compiler.
 *
 * <p>
 * The constructor resolves an embedded {@code simplebytecode-compiler.jar} and the
 * current Java executable, then builds a command that runs the compiler with the source
 * file. {@link #isAvailable()} reports whether both pieces are present, allowing
 * {@link io.simpleide.language.config.LangSetup} to register the JVM languages.
 *
 * <p>
 * See docs/reference/LANGUAGES.md for the runtime requirements.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public abstract class AbstractBytecodeLanguage extends AbstractLanguage {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBytecodeLanguage.class);

	private static final String COMPILER_JAR_RESOURCE = "simplebytecode-compiler.jar";

	private static final Path COMPILER_JAR = getCompilerJarPath();

	/**
	 * Creates a bytecode language definition for a source file.
	 * @param filePath the source file to execute
	 * @param friendlyName the human-readable language name
	 * @param languageConstant the syntax highlighting constant
	 * @throws IllegalArgumentException if {@code filePath} is {@code null}
	 */
	protected AbstractBytecodeLanguage(Path filePath, String friendlyName, String languageConstant) {
		if (filePath == null) {
			throw new IllegalArgumentException("Source file path must not be null.");
		}
		if (COMPILER_JAR == null) {
			LOGGER.error("[IDE] Cannot configure {} execution: compiler JAR is unavailable.", friendlyName);
			return;
		}

		this.langFriendly = friendlyName;
		this.langConstant = languageConstant;
		Path sourceFile = filePath.toAbsolutePath().normalize();
		Optional<Path> javaExecutable = JavaRuntime.jdkJavaExecutable();
		if (javaExecutable.isEmpty()) {
			LOGGER.error("[IDE] Cannot configure {} execution: Java executable was not found.", friendlyName);
			return;
		}

		this.pb = new ProcessBuilder(javaExecutable.get().toString(), "-jar", COMPILER_JAR.toString(),
				sourceFile.toString());
	}

	/**
	 * Reports whether the embedded bytecode compiler and a Java executable are available.
	 * @return {@code true} when JVM languages can run
	 */
	public static boolean isAvailable() {
		return COMPILER_JAR != null && JavaRuntime.jdkJavaExecutable().isPresent();
	}

	private static Path getCompilerJarPath() {
		try {
			return ResourceUtil.getResourcePath(AbstractBytecodeLanguage.class, COMPILER_JAR_RESOURCE);
		}
		catch (IOException exception) {
			Path buildDirectoryJar = compilerJarFromBuildDirectory();
			if (buildDirectoryJar != null) {
				return buildDirectoryJar;
			}
			LOGGER.error("[IDE] Could not locate the embedded simplebytecode compiler JAR: {}", COMPILER_JAR_RESOURCE,
					exception);
			return null;
		}
	}

	/**
	 * Locates the compiler fat JAR in the sibling module build directory. This keeps test
	 * runs and development launches working before the packaging phase copies the JAR
	 * into the resources.
	 * @return the newest compiler fat JAR, or {@code null} when none exists
	 */
	private static Path compilerJarFromBuildDirectory() {
		Path buildDirectory = Paths.get("..", "simplebytecode-compiler", "target").toAbsolutePath();
		if (!Files.isDirectory(buildDirectory)) {
			return null;
		}
		try (java.util.stream.Stream<Path> candidates = java.nio.file.Files.list(buildDirectory)
			.filter(path -> path.getFileName().toString().endsWith("-jar-with-dependencies.jar")
					&& path.getFileName().toString().startsWith("simplebytecode-compiler"))) {
			return candidates.max(java.util.Comparator.comparingLong(path -> path.toFile().lastModified()))
				.orElse(null);
		}
		catch (IOException exception) {
			return null;
		}
	}

}
