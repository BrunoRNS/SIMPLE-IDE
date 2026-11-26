package io.simpleide.dap;

import java.util.Locale;
import java.util.Objects;

/**
 * Download location of a debug adapter for one operating system and architecture.
 *
 * <p>
 * Entries are read from {@code registry.json}. The {@code os} and {@code arch} values
 * accept the aliases used by the language platform detection, such as {@code linux} and
 * {@code x64} or {@code aarch64}.
 *
 * @param os operating system family
 * @param arch architecture label
 * @param url HTTPS download URL of the adapter package
 * @param archiveType archive extension such as {@code zip} or {@code tar.gz}, or
 * {@code null} when the download is the adapter executable itself
 * @author BrunoRNS
 * @since v1.1.0
 */
public record DapDownload(String os, String arch, String url, String archiveType) {

	/**
	 * Creates a validated download entry.
	 * @param os operating system family
	 * @param arch architecture label
	 * @param url HTTPS download URL
	 * @param archiveType archive extension, or {@code null} for a single file
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapDownload {
		Objects.requireNonNull(os, "os");
		Objects.requireNonNull(arch, "arch");
		Objects.requireNonNull(url, "url");
	}

	/**
	 * Returns whether this entry matches a platform.
	 * @param platformOs platform operating system family
	 * @param platformArch platform architecture label
	 * @return {@code true} when both values match, including the known aliases
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean matches(String platformOs, String platformArch) {
		if (platformOs == null || platformArch == null) {
			return false;
		}
		return matchesOs(platformOs) && matchesArch(platformArch);
	}

	private boolean matchesOs(String platformOs) {
		String expected = os.toLowerCase(Locale.ROOT);
		String actual = platformOs.toLowerCase(Locale.ROOT);
		if (expected.equals(actual)) {
			return true;
		}
		if ("macos".equals(expected)) {
			return "darwin".equals(actual) || "mac".equals(actual);
		}
		if ("windows".equals(expected)) {
			return "win".equals(actual);
		}
		return false;
	}

	private boolean matchesArch(String platformArch) {
		String expected = arch.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
		String actual = platformArch.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
		if (expected.equals(actual)) {
			return true;
		}
		if (isX64(actual)) {
			return isX64(expected);
		}
		if (isArm64(actual)) {
			return isArm64(expected);
		}
		return false;
	}

	private static boolean isX64(String value) {
		return "x64".equals(value) || "x8664".equals(value) || "amd64".equals(value);
	}

	private static boolean isArm64(String value) {
		return "arm64".equals(value) || "aarch64".equals(value);
	}

}
