package io.simpleide.language.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Signals a fatal failure while starting, running, or stopping a language process.
 *
 * <p>
 * The exception extends {@link UncheckedIOException} so language runners can propagate
 * process failures without checked exception handling. It may also carry the offending
 * {@link Process}.
 *
 * <p>
 * Covered by {@code ProcessFatalExceptionTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class ProcessFatalException extends UncheckedIOException {

	private final Process processCause;

	/**
	 * Creates an exception from an input/output failure.
	 * @param message the detail message
	 * @param cause the underlying I/O failure
	 */
	public ProcessFatalException(String message, IOException cause) {
		super(message, cause);
		this.processCause = null;
	}

	/**
	 * Creates an exception associated with a specific process.
	 * @param message the detail message
	 * @param pcause the process that could not complete
	 */
	public ProcessFatalException(String message, Process pcause) {
		super(message, new IOException(message));
		this.processCause = pcause;
	}

	/**
	 * Returns the underlying I/O failure.
	 * @return the I/O failure backing this exception
	 */
	@Override
	public IOException getCause() {
		return (IOException) super.getCause();
	}

	/**
	 * Returns the process associated with this failure, when available.
	 * @return the offending process, or {@code null} when none was recorded
	 */
	public Process getProcessCause() {
		return this.processCause;
	}

}
