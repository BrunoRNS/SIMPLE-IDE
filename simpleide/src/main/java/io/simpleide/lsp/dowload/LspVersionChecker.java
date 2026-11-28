package io.simpleide.lsp.dowload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.utils.JavaRuntime;

/** Executes configured language version commands and compares version numbers. */
/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LspVersionChecker {

	private static final Logger LOGGER = LoggerFactory.getLogger(LspVersionChecker.class);

	private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)*)(?!\\d)");

	private LspVersionChecker() {
	}

	static String checkMinimumVersion(LspServer server) {
		String minimumVersion = server.getMinimumVersion();
		String versionCommand = server.getVersionCommand();
		if (minimumVersion == null || minimumVersion.isBlank() || versionCommand == null || versionCommand.isBlank()) {
			return null;
		}
		String detectedVersion = runVersionCommand(server);
		if (compareVersions(detectedVersion, minimumVersion) < 0) {
			throw new IllegalStateException(server.getName() + " requires language version " + minimumVersion
					+ " or newer; detected " + detectedVersion + ".");
		}
		return detectedVersion;
	}

	static int compareVersions(String actual, String minimum) {
		int[] actualParts = extractVersion(actual);
		int[] minimumParts = extractVersion(minimum);
		for (int index = 0; index < Math.max(actualParts.length, minimumParts.length); index++) {
			int actualPart = index < actualParts.length ? actualParts[index] : 0;
			int minimumPart = index < minimumParts.length ? minimumParts[index] : 0;
			if (actualPart != minimumPart) {
				return Integer.compare(actualPart, minimumPart);
			}
		}
		return 0;
	}

	static String runVersionCommand(LspServer server) {
		List<String> command = new ArrayList<>();
		String executable = server.getVersionCommand();
		command.add(JavaRuntime.isJavaCommand(executable)
				? JavaRuntime.javaExecutable().map(java.nio.file.Path::toString).orElse(executable) : executable);
		if (server.getVersionArguments() != null) {
			command.addAll(server.getVersionArguments());
		}
		java.nio.file.Path outputFile;
		try {
			outputFile = Files.createTempFile("simpleide-lsp-version-", ".log");
		}
		catch (IOException error) {
			throw new IllegalStateException("Unable to create output file for language version command: " + command,
					error);
		}
		Process process = null;
		try {
			process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Language version command timed out: " + command);
			}
			String result = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
			if (process.exitValue() != 0) {
				throw new IllegalStateException(
						"Language version command failed with exit code " + process.exitValue() + ": " + result);
			}
			if (result.isBlank()) {
				throw new IllegalStateException("Language version command returned no output: " + command);
			}
			return result;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while checking language version.", error);
		}
		catch (IOException error) {
			throw new IllegalStateException("Unable to read language version command output.", error);
		}
		finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
			try {
				Files.deleteIfExists(outputFile);
			}
			catch (IOException error) {
				outputFile.toFile().deleteOnExit();
				LOGGER.warn("Unable to delete language version output file {}.", outputFile, error);
			}
		}
	}

	private static int[] extractVersion(String value) {
		Matcher matcher = VERSION_PATTERN.matcher(value);
		if (!matcher.find()) {
			throw new IllegalArgumentException("No numeric version found in: " + value);
		}
		String[] parts = matcher.group(1).split("\\.");
		int[] version = new int[parts.length];
		try {
			for (int index = 0; index < parts.length; index++) {
				version[index] = Integer.parseInt(parts[index]);
			}
		}
		catch (NumberFormatException error) {
			throw new IllegalArgumentException("Invalid version: " + value, error);
		}
		return version;
	}

}
