package io.simpleide.ui.io;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.simpleide.language.io.Input;

/**
 * Forwards lines typed in the I/O tab to the running language process.
 *
 * <p>
 * The component wraps the {@link io.simpleide.language} input API, so it never talks to a
 * process directly: every submitted line is queued in {@link Input}, and the language
 * runner keeps its own forwarder alive until the process ends. The I/O tab closes this
 * forwarder through {@link #close()} when it receives the execution-finished event.
 *
 * <p>
 * Covered by {@code InputForwarderTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class InputForwarder implements AutoCloseable {

	private final Input input;

	private final AtomicBoolean active = new AtomicBoolean(true);

	/**
	 * Creates a forwarder backed by the process-wide input queue.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public InputForwarder() {
		this(Input.getInstance());
	}

	/**
	 * Creates a forwarder backed by a specific queue.
	 * @param input input queue to publish to
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public InputForwarder(Input input) {
		this.input = Objects.requireNonNull(input, "input");
	}

	/**
	 * Queues one or more lines for the process.
	 *
	 * <p>
	 * Line endings are normalised, so a pasted block containing several lines is sent as
	 * separate process input lines. Empty and whitespace-only lines are valid input.
	 * @param text text to send, ignored when {@code null} or when the forwarder is closed
	 * @return {@code true} when at least one line was queued
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean submit(String text) {
		if (!active.get() || text == null) {
			return false;
		}
		for (String line : text.split("\\R")) {
			input.set(line);
		}
		return true;
	}

	/**
	 * Returns whether the forwarder still accepts input.
	 * @return {@code true} until {@link #close()} is called
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean isActive() {
		return active.get();
	}

	/**
	 * Stops accepting input.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	@Override
	public void close() {
		active.set(false);
	}

}
