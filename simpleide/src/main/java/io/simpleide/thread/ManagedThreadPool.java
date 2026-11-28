package io.simpleide.thread;

import java.util.concurrent.ExecutorService;

import io.simpleide.thread.nodes.NodePattern;

/**
 * Named, dedicated worker pool registered in the thread tree.
 *
 * <p>
 * A pool is created by {@link AbstractThreadMaster#createThreadPool(String, int)} and
 * owns one tree node that acts as the parent of its tasks. Closing the pool removes the
 * node, cancels its tasks and shuts the underlying executor down.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class ManagedThreadPool implements AutoCloseable {

	private final AbstractThreadMaster owner;

	private final NodePattern node;

	private final String name;

	ManagedThreadPool(AbstractThreadMaster owner, NodePattern node, String name) {
		this.owner = owner;
		this.node = node;
		this.name = name;
	}

	/**
	 * Submits a task to this pool without a close callback.
	 * @param action work to execute on one of the pool workers
	 * @return the node that tracks the submitted task
	 */
	public NodePattern execute(Runnable action) {
		return execute(action, null);
	}

	/**
	 * Submits a task to this pool with an optional close callback.
	 * @param action work to execute on one of the pool workers
	 * @param onClose callback run when the task finishes, or {@code null}
	 * @return the node that tracks the submitted task
	 */
	public NodePattern execute(Runnable action, Runnable onClose) {
		return owner.executeOnPool(this, action, onClose);
	}

	/**
	 * Creates a managed executor whose tasks run on this pool's workers.
	 * @return executor service scoped to this pool
	 */
	public ExecutorService createExecutorService() {
		return owner.createExecutorService(node);
	}

	@Override
	public void close() {
		owner.closePool(this);
	}

	AbstractThreadMaster owner() {
		return owner;
	}

	NodePattern node() {
		return node;
	}

	String name() {
		return name;
	}

}
