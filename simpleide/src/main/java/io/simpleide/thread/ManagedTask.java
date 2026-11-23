package io.simpleide.thread;

import java.awt.EventQueue;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.validation.NodeExecutionValidation;

/**
 * Runnable wrapper that executes one thread tree node exactly once.
 *
 * <p>
 * The wrapper validates the execution context, guards against duplicate execution,
 * reports failures, runs the node's close action on the appropriate thread and notifies
 * the scheduler when the task finishes. Cancelling a queued task removes it from its
 * executor; cancelling a running background task interrupts its thread.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class ManagedTask implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(ManagedTask.class);

	private final NodePattern node;

	private final Consumer<ManagedTask> onFinished;

	private final AtomicBoolean started = new AtomicBoolean();

	private final AtomicBoolean closed = new AtomicBoolean();

	private final AtomicReference<TaskSnapshot> state = new AtomicReference<>(new TaskSnapshot(false, false, null));

	ManagedTask(NodePattern node, Consumer<ManagedTask> onFinished) {
		this.node = node;
		this.onFinished = onFinished;
	}

	BigInteger getNodeId() {
		return node.getId();
	}

	boolean isRunning() {
		return state.get().running();
	}

	@Override
	public void run() {
		if (!started.compareAndSet(false, true)) {
			return;
		}
		state.updateAndGet(snapshot -> snapshot.withExecution(true, Thread.currentThread()));
		try {
			NodeExecutionValidation.INSTANCE.validate(node);
			if (!state.get().cancelled()) {
				Runnable action = node.onStart();
				if (action != null) {
					action.run();
				}
			}
		}
		catch (RuntimeException | Error exception) {
			LOGGER.error("Managed thread node id={} failed.", node.getId(), exception);
			throw exception;
		}
		finally {
			state.updateAndGet(snapshot -> snapshot.withExecution(false, null));
			closeOnce();
			onFinished.accept(this);
		}
	}

	void cancel(Runnable removeFromQueue) {
		TaskSnapshot snapshot = state.updateAndGet(current -> current.withCancelled());
		Thread currentRunner = snapshot.runner();
		if (currentRunner != null && !node.isEDT() && currentRunner != Thread.currentThread()) {
			currentRunner.interrupt();
		}
		else if (!started.get()) {
			if (!node.isEDT()) {
				removeFromQueue.run();
			}
			closeOnce();
		}
	}

	private record TaskSnapshot(boolean cancelled, boolean running, Thread runner) {

		private TaskSnapshot withCancelled() {
			return new TaskSnapshot(true, running, runner);
		}

		private TaskSnapshot withExecution(boolean running, Thread runner) {
			return new TaskSnapshot(cancelled, running, runner);
		}

	}

	private void closeOnce() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		Runnable closeAction;
		try {
			closeAction = node.onClose();
		}
		catch (RuntimeException | Error exception) {
			LOGGER.error("Unable to create close action for node id={}.", node.getId(), exception);
			return;
		}
		if (closeAction == null) {
			return;
		}
		Runnable guardedClose = () -> {
			try {
				closeAction.run();
			}
			catch (RuntimeException | Error exception) {
				LOGGER.error("Close action for node id={} failed.", node.getId(), exception);
			}
		};
		if (node.isEDT() && !EventQueue.isDispatchThread()) {
			EventQueue.invokeLater(guardedClose);
		}
		else {
			guardedClose.run();
		}
	}

}
