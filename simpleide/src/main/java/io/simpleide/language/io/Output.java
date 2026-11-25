package io.simpleide.language.io;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.holder.OutputHolder;

/**
 * Thread-safe queue of lines published by a running language process.
 *
 * <p>
 * The language runner enqueues every line read from the process, including merged
 * standard error, so callers can consume program output without blocking the process. Use
 * {@link #getInstance()} to access the process-wide queue.
 *
 * <p>
 * Lines carry an {@link OutputType}. Listeners registered with
 * {@link #addListener(Consumer)} receive every line together with its type; they are
 * intended for UI components such as the terminal I/O tab and must return quickly because
 * they run on the publishing thread.
 *
 * <p>
 * Covered by {@code IOConcurrencyTest} and {@code IOOutputListenerTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author BrunoRNS
 * @since v1.1.0
 */
public class Output {

	/**
	 * Marker published when a running program is waiting for standard input.
	 */
	public static final String WAITING_FOR_STDIN = "{WAITING FOR STDIN}";

	/**
	 * Marker published when a debug session completed the protocol handshake.
	 */
	public static final String INITIALIZED = "{INITIALIZED}";

	/**
	 * Marker published when a debug session finished.
	 */
	public static final String FINISHED = "{FINISHED}";

	private static final Logger LOGGER = LoggerFactory.getLogger(Output.class);

	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

	private final CopyOnWriteArrayList<Consumer<OutputLine>> listeners = new CopyOnWriteArrayList<>();

	/**
	 * Enqueues a line read from the running process.
	 * @param text the line to publish, ignored when {@code null}
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void set(String text) {
		publish(OutputType.OUTPUT, text);
	}

	/**
	 * Publishes a typed line to every registered listener. Only ordinary program output
	 * is also placed in the legacy string queue consumed by {@link #get(long)}.
	 * @param type classification of the line, defaults to {@link OutputType#OUTPUT} when
	 * {@code null}
	 * @param text the line to publish, ignored when {@code null}
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void publish(OutputType type, String text) {
		if (text == null) {
			return;
		}
		OutputType effectiveType = type == null ? OutputType.OUTPUT : type;
		if (effectiveType == OutputType.OUTPUT) {
			queue.offer(text);
		}
		OutputLine line = new OutputLine(effectiveType, text);
		for (Consumer<OutputLine> listener : listeners) {
			try {
				listener.accept(line);
			}
			catch (RuntimeException error) {
				LOGGER.warn("An output listener failed to handle a published line.", error);
			}
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
	 * Registers a listener that receives every published line with its type.
	 * @param listener listener to register, ignored when {@code null}
	 * @return a handle that removes the listener when closed
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public AutoCloseable addListener(Consumer<OutputLine> listener) {
		if (listener == null) {
			return () -> {
			};
		}
		listeners.add(listener);
		return () -> listeners.remove(listener);
	}

	/**
	 * Removes a previously registered listener.
	 * @param listener listener to remove, ignored when {@code null}
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void removeListener(Consumer<OutputLine> listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}

	/**
	 * Returns the process-wide output queue.
	 * @return the shared {@link Output} instance
	 */
	public static Output getInstance() {
		return OutputHolder.INSTANCE;
	}

	/**
	 * One typed line published by a language process.
	 *
	 * @param type classification of the line
	 * @param text line text without the type prefix
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public record OutputLine(OutputType type, String text) {

		/**
		 * Creates a validated output line.
		 * @param type classification of the line, defaults to {@link OutputType#OUTPUT}
		 * @param text line text
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		public OutputLine {
			Objects.requireNonNull(type, "type");
			Objects.requireNonNull(text, "text");
		}

	}

}
