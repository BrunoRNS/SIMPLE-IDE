package io.simpleide.language.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Supported operating system and architecture combinations.
 *
 * <p>
 * Each constant carries a display name, a normalised architecture label, and an operating
 * system family. The static helpers expose the cached platform lists used by toolchain
 * settings and detect the current platform from JVM properties.
 *
 * <p>
 * Covered by {@code LangPlatformTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public enum LangPlatform {

	WINDOWSX86("Windows x86", "x86", "windows"), WINDOWSX64UNICODE("Windows x64 (Unicode)", "x64", "windows"),
	WINDOWSARM64("Windows ARM64", "arm64", "windows"),

	LINUXX86("Linux x86", "x86", "linux"), LINUXX64("Linux x64", "x64", "linux"),
	LINUXARM64("Linux ARM64", "arm64", "linux"),

	MACOSINTEL("macOS Intel", "x64", "macos"), MACOSAARCH64("macOS Apple Silicon", "aarch64", "macos"),

	UNKNOWN("Unknown Platform", "unknown", "unknown");

	private final String displayName;

	private final String architecture;

	private final String os;

	LangPlatform(String displayName, String architecture, String os) {
		this.displayName = displayName;
		this.architecture = architecture;
		this.os = os;
	}

	/**
	 * Returns the human-readable platform name.
	 * @return the display name
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Returns the normalised architecture label.
	 * @return the architecture label
	 */
	public String getArchitecture() {
		return architecture;
	}

	/**
	 * Returns the operating system family.
	 * @return the operating system name
	 */
	public String getOs() {
		return os;
	}

	private static final List<String> CACHED_OPERATING_SYSTEMS;

	private static final List<String> CACHED_ARCHITECTURES;

	private static final Map<String, List<LangPlatform>> CACHED_PLATFORMS_BY_OS;

	static {
		List<LangPlatform> validPlatforms = Arrays.stream(values())
			.filter(p -> p != UNKNOWN)
			.collect(Collectors.toList());

		CACHED_OPERATING_SYSTEMS = Collections
			.unmodifiableList(validPlatforms.stream().map(LangPlatform::getOs).distinct().collect(Collectors.toList()));

		CACHED_ARCHITECTURES = Collections.unmodifiableList(
				validPlatforms.stream().map(LangPlatform::getArchitecture).distinct().collect(Collectors.toList()));

		CACHED_PLATFORMS_BY_OS = Collections
			.unmodifiableMap(validPlatforms.stream().collect(Collectors.groupingBy(p -> p.getOs().toLowerCase())));
	}

	/**
	 * Returns the distinct operating system families supported by the known platforms.
	 * @return an unmodifiable list of operating system names
	 */
	public static List<String> getAvailableOperatingSystems() {
		return CACHED_OPERATING_SYSTEMS;
	}

	/**
	 * Returns the distinct architecture labels supported by the known platforms.
	 * @return an unmodifiable list of architecture labels
	 */
	public static List<String> getAvailableArchitectures() {
		return CACHED_ARCHITECTURES;
	}

	/**
	 * Returns the platforms available for an operating system family.
	 * @param targetOs the operating system name, case-insensitive
	 * @return the matching platforms, or an empty list when {@code targetOs} is
	 * {@code null} or unknown
	 */
	public static List<LangPlatform> filterByOs(String targetOs) {
		if (targetOs == null)
			return Collections.emptyList();
		return CACHED_PLATFORMS_BY_OS.getOrDefault(targetOs.toLowerCase(), Collections.emptyList());
	}

	/**
	 * Detects the platform of the running JVM.
	 * @return the matching platform, or {@link #UNKNOWN} when it cannot be determined
	 * @throws IllegalStateException if the JDK executable cannot be inspected on macOS
	 */
	public static LangPlatform getCurrentPlatform() {
		String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		String jdkArch = getJdkArchitecture(osName);

		if (osName.contains("win")) {
			if (jdkArch.contains("aarch64") || jdkArch.contains("arm64")) {
				return WINDOWSARM64;
			}
			else if (jdkArch.contains("64")) {
				return WINDOWSX64UNICODE;
			}
			else {
				return WINDOWSX86;
			}
		}
		else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
			if (jdkArch.contains("aarch64") || jdkArch.contains("arm64")) {
				return LINUXARM64;
			}
			else if (jdkArch.contains("64")) {
				return LINUXX64;
			}
			else {
				return LINUXX86;
			}
		}
		else if (osName.contains("mac") || osName.contains("darwin")) {
			if (jdkArch.contains("aarch64") || jdkArch.contains("arm64")) {
				return MACOSAARCH64;
			}
			else if (jdkArch.contains("64")) {
				return MACOSINTEL;
			}
		}

		return UNKNOWN;
	}

	private static String getJdkArchitecture(String osName) {
		if (!osName.contains("mac") && !osName.contains("darwin")) {
			return System.getProperty("os.arch").toLowerCase(Locale.ROOT);
		}

		Path javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java");
		Process process;
		try {
			process = new ProcessBuilder("/usr/bin/file", "-L", "-b", javaExecutable.toString())
				.redirectErrorStream(true)
				.start();
			String description = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
				.toLowerCase(Locale.ROOT);
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new IllegalStateException("Could not inspect JDK executable: " + description);
			}
			return architectureFromExecutableDescription(description,
					System.getProperty("os.arch").toLowerCase(Locale.ROOT));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not inspect JDK executable at " + javaExecutable, e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while inspecting JDK executable at " + javaExecutable, e);
		}
	}

	/**
	 * Resolves the architecture from a description of the JDK executable.
	 * @param description the output of the platform's file inspection tool
	 * @param fallbackArchitecture the architecture to use for universal binaries
	 * @return the resolved architecture label
	 * @throws IllegalStateException if the description names an unsupported architecture
	 */
	static String architectureFromExecutableDescription(String description, String fallbackArchitecture) {
		String normalizedDescription = description.toLowerCase(Locale.ROOT);
		if (normalizedDescription.contains("universal")) {
			return fallbackArchitecture.toLowerCase(Locale.ROOT);
		}
		if (normalizedDescription.contains("x86_64")) {
			return "x86_64";
		}
		if (normalizedDescription.contains("arm64")) {
			return "arm64";
		}
		throw new IllegalStateException("Unsupported JDK executable architecture: " + description);
	}

}
