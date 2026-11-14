package io.simpleide.lsp.client;

import java.io.IOException;

/**
 * Signals that a language server process exited before initialization completed.
 *
 * <p>
 * When the process exits before the LSP handshake finishes, the exit code is kept so
 * callers can detect a JVM compatibility failure. Exit code {@code 13} usually means the
 * Java runtime used to launch the server is older than the server requires.
 *
 * <p>
 * Covered by {@code LspServerStartupExceptionTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public final class LspServerStartupException extends IOException {

	private static final long serialVersionUID = 1L;

	private final int exitCode;

	/**
	 * Creates an exception for a server process that exited with the given code.
	 * @param exitCode process exit code
	 */
	public LspServerStartupException(int exitCode) {
		super(createMessage(exitCode));
		this.exitCode = exitCode;
	}

	/**
	 * Returns the exit code of the server process.
	 * @return the process exit code
	 */
	public int getExitCode() {
		return exitCode;
	}

	/**
	 * Tells whether the exit code indicates a Java runtime compatibility failure.
	 * @return {@code true} when the exit code is {@code 13}
	 */
	public boolean isJvmCompatibilityFailure() {
		return exitCode == 13;
	}

	private static String createMessage(int exitCode) {
		String message = "Language server exited before initialization (code " + exitCode + ").";
		if (exitCode == 13) {
			message += " The Java runtime used to launch it is probably older than the server requires."
					+ " On institutional machines with a fixed Java runtime this is a known issue.";
		}
		return message;
	}

}
