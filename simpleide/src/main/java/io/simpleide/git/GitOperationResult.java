package io.simpleide.git;

import java.util.Objects;

/**
 * Result of a local Git command.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public record GitOperationResult(boolean success, int exitCode, String output, String error) {

	/**
	 * Creates an operation result.
	 * @param success whether the command succeeded
	 * @param exitCode process exit code
	 * @param output standard output
	 * @param error standard error
	 */
	public GitOperationResult {
		output = Objects.requireNonNullElse(output, "");
		error = Objects.requireNonNullElse(error, "");
	}

	/**
	 * Combines stdout and stderr for display.
	 * @return combined human-readable output
	 */
	public String combinedOutput() {
		return (output.isBlank() ? "" : output.trim() + "\n") + error.trim();
	}
}
