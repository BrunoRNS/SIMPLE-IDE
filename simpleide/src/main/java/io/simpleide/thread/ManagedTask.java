package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.validation.NodeExecutionValidation;
import java.awt.EventQueue;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ManagedTask implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManagedTask.class);

  private final NodePattern node;
  private final Consumer<ManagedTask> onFinished;
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile boolean cancelled;
  private volatile boolean running;
  private volatile Thread runner;

  ManagedTask(NodePattern node, Consumer<ManagedTask> onFinished) {
    this.node = node;
    this.onFinished = onFinished;
  }

  BigInteger getNodeId() {
    return node.getId();
  }

  boolean isRunning() {
    return running;
  }

  @Override
  public void run() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    runner = Thread.currentThread();
    running = true;
    try {
      NodeExecutionValidation.INSTANCE.validate(node);
      if (!cancelled) {
        Runnable action = node.onStart();
        if (action != null) {
          action.run();
        }
      }
    } catch (RuntimeException | Error exception) {
      LOGGER.error("Managed thread node id={} failed.", node.getId(), exception);
      throw exception;
    } finally {
      running = false;
      runner = null;
      closeOnce();
      onFinished.accept(this);
    }
  }

  void cancel(Runnable removeFromQueue) {
    cancelled = true;
    Thread currentRunner = runner;
    if (currentRunner != null && !node.isEDT() && currentRunner != Thread.currentThread()) {
      currentRunner.interrupt();
    } else if (!started.get()) {
      if (!node.isEDT()) {
        removeFromQueue.run();
      }
      closeOnce();
    }
  }

  private void closeOnce() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    Runnable closeAction;
    try {
      closeAction = node.onClose();
    } catch (RuntimeException | Error exception) {
      LOGGER.error("Unable to create close action for node id={}.", node.getId(), exception);
      return;
    }
    if (closeAction == null) {
      return;
    }
    Runnable guardedClose =
        () -> {
          try {
            closeAction.run();
          } catch (RuntimeException | Error exception) {
            LOGGER.error("Close action for node id={} failed.", node.getId(), exception);
          }
        };
    if (node.isEDT() && !EventQueue.isDispatchThread()) {
      EventQueue.invokeLater(guardedClose);
    } else {
      guardedClose.run();
    }
  }
}
