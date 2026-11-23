package io.simpleide.thread;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger LOGGER = LoggerFactory.getLogger(ThreadMaster.class);

	private final NodePattern root;

	private final List<NamedShutdownAction> shutdownActions = new CopyOnWriteArrayList<>();

	private final AtomicBoolean shutdownHookInstalled = new AtomicBoolean();

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
	 * Registers an action that runs once when the JVM shuts down.
	 *
	 * <p>
	 * The shutdown hook thread is created here, inside the thread manager, so application
	 * code never creates its own thread. Actions are executed in registration order and a
	 * failing action does not prevent the remaining actions from running.
	 * @param name descriptive action name used in log messages
	 * @param action action to execute during shutdown, must not be {@code null}
	 */
	public void registerShutdownAction(String name, Runnable action) {
		if (action == null) {
			throw new IllegalArgumentException("Shutdown action must not be null.");
		}
		String actionName = name == null || name.isBlank() ? "unnamed" : name;
		shutdownActions.add(new NamedShutdownAction(actionName, action));
		if (shutdownHookInstalled.compareAndSet(false, true)) {
			Runtime.getRuntime().addShutdownHook(new Thread(this::runShutdownActions, "simpleide-shutdown"));
		}
	}

	private void runShutdownActions() {
		for (NamedShutdownAction shutdownAction : shutdownActions) {
			try {
				shutdownAction.action().run();
			}
			catch (RuntimeException error) {
				LOGGER.warn("Shutdown action '{}' failed.", shutdownAction.name(), error);
			}
		}
	}

	private record NamedShutdownAction(String name, Runnable action) {
	}

	/**
	 * Returns the shared thread manager, creating it on first use.
	 * @return the process-wide {@code ThreadMaster}
	 */
	public static ThreadMaster getInstance() {
		return ThreadMasterHolder.INSTANCE;
	}

}
