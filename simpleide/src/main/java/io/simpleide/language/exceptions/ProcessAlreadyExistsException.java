package io.simpleide.language.exceptions;

/**
 * Thrown when a language is started while it still has a live process.
 *
 * <p>
 * The exception retains the process that prevented the new execution so callers can
 * inspect or stop it.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class ProcessAlreadyExistsException extends RuntimeException {

	private volatile Process actualProcess = null;

	/**
	 * Creates an exception that reports the process already in use.
	 * @param message the detail message
	 * @param actual the process that is still running
	 */
	public ProcessAlreadyExistsException(String message, Process actual) {
		super(message);
		this.actualProcess = actual;
	}

	/**
	 * Returns the process that is already running.
	 * @return the existing process
	 */
	public Process getActualProcess() {
		return this.actualProcess;
	}

}
