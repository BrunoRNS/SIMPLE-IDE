package io.simpleide.language.io;

import io.simpleide.language.io.holder.OutputHolder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe queue of lines published by a running language process.
 *
 * <p>
 * The language runner enqueues every line read from the process, including merged
 * standard error, so callers can consume program output without blocking the process. Use
 * {@link #getInstance()} to access the process-wide queue.
 *
 * <p>
 * Covered by {@code IOConcurrencyTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class Output {

	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

	/**
	 * Enqueues a line read from the running process.
	 * @param text the line to publish, ignored when {@code null}
	 */
	public void set(String text) {
		if (text != null) {
			queue.offer(text);
		}
	}

	/**
	 * Waits for the next line published by the process.
	 * @param waitSeconds the maximum time to wait, in seconds
	 * @return the next line, or {@code null} when the timeout expires
	 * @throws InterruptedException if the calling thread is interrupted while waiting
	 */
	public String get(long waitSeconds) throws InterruptedException {
		return queue.poll(waitSeconds, TimeUnit.SECONDS);
	}

	/**
	 * Returns the process-wide output queue.
	 * @return the shared {@link Output} instance
	 */
	public static Output getInstance() {
		return OutputHolder.INSTANCE;
	}

}
