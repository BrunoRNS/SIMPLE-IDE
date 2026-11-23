package io.simpleide.language.io;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.simpleide.language.io.holder.InputHolder;

/**
 * Thread-safe queue of lines sent to a running language process.
 *
 * <p>
 * The language runner polls this queue on a background forwarder and writes each line to
 * the process standard input. Use {@link #getInstance()} to access the process-wide
 * queue.
 *
 * <p>
 * Covered by {@code IOConcurrencyTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class Input {

	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

	/**
	 * Enqueues a line for the running process.
	 * @param text the line to send, ignored when {@code null}
	 */
	public void set(String text) {
		if (text != null) {
			queue.offer(text);
		}
	}

	/**
	 * Waits for the next line to send to the process.
	 * @param waitSeconds the maximum time to wait, in seconds
	 * @return the next line, or {@code null} when the timeout expires
	 * @throws InterruptedException if the calling thread is interrupted while waiting
	 */
	public String get(long waitSeconds) throws InterruptedException {
		return queue.poll(waitSeconds, TimeUnit.SECONDS);
	}

	/**
	 * Returns the process-wide input queue.
	 * @return the shared {@link Input} instance
	 */
	public static Input getInstance() {
		return InputHolder.INSTANCE;
	}

}
