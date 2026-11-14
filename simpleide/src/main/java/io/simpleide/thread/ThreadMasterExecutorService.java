package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class ThreadMasterExecutorService extends AbstractExecutorService {
  private final AbstractThreadMaster threadMaster;
  private final NodePattern parent;
  private final Object stateLock = new Object();
  private final Map<RunnableNode, Runnable> tasks = new LinkedHashMap<>();
  private boolean shutdown;

  ThreadMasterExecutorService(AbstractThreadMaster threadMaster, NodePattern parent) {
    this.threadMaster = threadMaster;
    this.parent = parent;
  }

  @Override
  public void execute(Runnable command) {
    Objects.requireNonNull(command, "command");
    AtomicReference<RunnableNode> nodeReference = new AtomicReference<>();
    RunnableNode node = new RunnableNode(parent, command, () -> taskFinished(nodeReference.get()));
    nodeReference.set(node);

    synchronized (stateLock) {
      if (shutdown) {
        throw new RejectedExecutionException("ThreadMaster executor has been shut down.");
      }
      tasks.put(node, command);
      try {
        threadMaster.register(node);
      } catch (RuntimeException error) {
        tasks.remove(node);
        stateLock.notifyAll();
        throw new RejectedExecutionException("Unable to register task with ThreadMaster.", error);
      }
    }
  }

  @Override
  public void shutdown() {
    synchronized (stateLock) {
      shutdown = true;
      stateLock.notifyAll();
    }
  }

  @Override
  public List<Runnable> shutdownNow() {
    List<Map.Entry<RunnableNode, Runnable>> pending;
    synchronized (stateLock) {
      shutdown = true;
      pending = new ArrayList<>(tasks.entrySet());
      stateLock.notifyAll();
    }
    for (Map.Entry<RunnableNode, Runnable> task : pending) {
      threadMaster.removeNodeCascade(task.getKey());
    }
    return pending.stream().map(Map.Entry::getValue).toList();
  }

  @Override
  public boolean isShutdown() {
    synchronized (stateLock) {
      return shutdown;
    }
  }

  @Override
  public boolean isTerminated() {
    synchronized (stateLock) {
      return shutdown && tasks.isEmpty();
    }
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long remainingNanos = unit.toNanos(timeout);
    long deadline = System.nanoTime() + remainingNanos;
    synchronized (stateLock) {
      while (!shutdown || !tasks.isEmpty()) {
        if (remainingNanos <= 0) {
          return false;
        }
        TimeUnit.NANOSECONDS.timedWait(stateLock, remainingNanos);
        remainingNanos = deadline - System.nanoTime();
      }
      return true;
    }
  }

  private void taskFinished(RunnableNode node) {
    threadMaster.removeNodeCascade(node);
    synchronized (stateLock) {
      tasks.remove(node);
      if (tasks.isEmpty()) {
        stateLock.notifyAll();
      }
    }
  }
}
