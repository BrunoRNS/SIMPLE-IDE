package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;

/**
 * Process-wide thread manager shared by the application and its subsystems.
 *
 * <p>
 * The singleton is created on demand by {@link #getInstance()} and rooted at a node with
 * level zero that executes on the Swing Event Dispatch Thread. Use the inherited methods
 * to register nodes, run a task on the EDT or a background pool, schedule a delayed task
 * and shut the manager down. The simpler facade used by most embedding applications is
 * {@code io.simpleide.api.Threads}.
 *
 * <p>
 * Covered by {@code ThreadMasterTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/threading.md">docs/api/threading.md</a>
 */
public final class ThreadMaster extends AbstractThreadMaster {

	private final NodePattern root;

	public ThreadMaster(NodePattern root) {
		super(root);
		this.root = root;
	}

	/**
	 * Returns the root pattern of the thread tree.
	 * @return the root node, which runs on the EDT at level zero
	 */
	public NodePattern getROOT() {
		return root;
	}

	/**
	 * Returns the shared thread manager, creating it on first use.
	 * @return the process-wide {@code ThreadMaster}
	 */
	public static ThreadMaster getInstance() {
		return ThreadMasterHolder.INSTANCE;
	}

}
