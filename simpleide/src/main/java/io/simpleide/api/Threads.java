package io.simpleide.api;

import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Small facade over {@link ThreadMaster}.
 *
 * <p>{@link ThreadMaster} is a process-wide singleton. The facade initializes it on first use.
 * After {@link #shutdown()} is called the singleton cannot be initialized again; this matches the
 * lifecycle of the desktop application.
 */
public final class Threads {

  private Threads() {}

  /**
   * Returns the initialized thread manager.
   *
   * @return the shared {@link ThreadMaster}
   */
  public static ThreadMaster master() {
    ThreadMaster master = ThreadMaster.getInstance();
    master.initialize();
    return master;
  }

  /**
   * Runs an action on the managed background worker pool.
   *
   * @param action action to run
   * @return the registered node
   */
  public static NodePattern background(Runnable action) {
    return background(action, null);
  }

  /**
   * Runs an action on the managed background worker pool.
   *
   * @param action action to run
   * @param onClose optional callback invoked when the node is removed or the manager shuts down
   * @return the registered node
   */
  public static NodePattern background(Runnable action, Runnable onClose) {
    Objects.requireNonNull(action, "action");
    ThreadMaster master = master();
    RunnableNode node = new RunnableNode(action, onClose);
    master.register(node);
    return node;
  }

  /**
   * Runs an action on the Swing Event Dispatch Thread.
   *
   * @param action action to run
   * @return the temporary EDT node
   */
  public static NodePattern onEdt(Runnable action) {
    return onEdt(action, null);
  }

  /**
   * Runs an action on the Swing Event Dispatch Thread.
   *
   * @param action action to run
   * @param onClose optional callback invoked after the action finishes
   * @return the temporary EDT node
   */
  public static NodePattern onEdt(Runnable action, Runnable onClose) {
    Objects.requireNonNull(action, "action");
    return master().executeOnEDT(action, onClose);
  }

  /**
   * Creates a named managed worker pool.
   *
   * @param name non-blank pool name used in logs
   * @param workers number of workers; must be positive
   * @return a managed pool; close it to remove its node
   */
  public static ManagedThreadPool pool(String name, int workers) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("The pool name must not be blank.");
    }
    if (workers < 1) {
      throw new IllegalArgumentException("The pool must have at least one worker.");
    }
    return master().createThreadPool(name, workers);
  }

  /**
   * Schedules an action on the shared thread tree.
   *
   * @param action action to run
   * @param delay delay before the action is scheduled
   * @param unit delay unit
   * @return the scheduled future
   */
  public static ScheduledFuture<?> schedule(Runnable action, long delay, TimeUnit unit) {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(unit, "unit");
    if (delay < 0) {
      throw new IllegalArgumentException("The delay must not be negative.");
    }
    ThreadMaster master = master();
    return master.schedule(master.getROOT(), action, delay, unit);
  }

  /**
   * Returns whether a node is currently executing.
   *
   * @param node node to inspect
   * @return {@code null} when the node is not registered, {@code true} while running, otherwise
   *     {@code false}
   */
  public static Boolean isRunning(NodePattern node) {
    return ThreadMaster.getInstance().isNodeRunning(node);
  }

  /** Shuts down the shared thread manager. This is irreversible for the current process. */
  public static void shutdown() {
    ThreadMaster.getInstance().shutdown();
  }
}
