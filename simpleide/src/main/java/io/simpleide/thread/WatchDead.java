package io.simpleide.thread;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WatchDead {

  private static final Logger LOGGER = LoggerFactory.getLogger(WatchDead.class);
  private static final long DEFAULT_POLL_MILLIS = 5000;

  private static volatile WatchDead instance;

  private final ManagedThreadPool workers;
  private final long pollMillis;
  private final Object lock = new Object();
  private final Map<String, Watch> watches = new LinkedHashMap<>();

  private boolean running;

  WatchDead(ThreadMaster threads, long pollMillis) {
    if (threads == null) {
      throw new IllegalArgumentException("ThreadMaster must not be null.");
    }
    if (pollMillis < 1) {
      throw new IllegalArgumentException("Poll interval must be positive.");
    }
    this.pollMillis = pollMillis;
    this.workers = threads.createThreadPool("watch-dead", 1);
  }

  public static void watch(String label, BooleanSupplier alive, Supplier<String> diagnostics) {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("Watch label must not be blank.");
    }
    if (alive == null) {
      throw new IllegalArgumentException("Alive supplier must not be null.");
    }
    try {
      instance().watchProcess(label, alive, diagnostics == null ? () -> "" : diagnostics);
    } catch (RuntimeException | LinkageError exception) {
      LOGGER.error(
          "Unable to start background process watch for {}; the application will continue.",
          label,
          exception);
    }
  }

  private static WatchDead instance() {
    WatchDead current = instance;
    if (current == null) {
      synchronized (WatchDead.class) {
        current = instance;
        if (current == null) {
          current = new WatchDead(ThreadMaster.getInstance(), DEFAULT_POLL_MILLIS);
          instance = current;
        }
      }
    }
    return current;
  }

  void watchProcess(String label, BooleanSupplier alive, Supplier<String> diagnostics) {
    Watch watch = new Watch(label, alive, diagnostics, System.nanoTime());
    boolean startMonitor = false;
    synchronized (lock) {
      if (watches.putIfAbsent(label, watch) != null) {
        return;
      }
      if (!running) {
        running = true;
        startMonitor = true;
      }
    }

    LOGGER.warn(
        "Detached process is still running; watching it in the background: label={}.", label);
    if (!startMonitor) {
      return;
    }
    try {
      workers.execute(this::monitor);
    } catch (RuntimeException | LinkageError exception) {
      synchronized (lock) {
        running = false;
        watches.remove(label, watch);
      }
      LOGGER.error(
          "Unable to start background process watcher for {}; the application will continue.",
          label,
          exception);
    }
  }

  int watchCount() {
    synchronized (lock) {
      return watches.size();
    }
  }

  private void monitor() {
    try {
      while (true) {
        List<Watch> snapshot;
        synchronized (lock) {
          if (watches.isEmpty()) {
            running = false;
            return;
          }
          snapshot = new ArrayList<>(watches.values());
        }
        snapshot.forEach(this::check);
        TimeUnit.MILLISECONDS.sleep(pollMillis);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      LOGGER.debug("Background process watcher interrupted.");
    } catch (RuntimeException exception) {
      LOGGER.error("Background process watcher failed; the application will continue.", exception);
    } finally {
      synchronized (lock) {
        running = false;
      }
    }
  }

  private void check(Watch watch) {
    boolean alive;
    try {
      alive = watch.alive.getAsBoolean();
    } catch (RuntimeException exception) {
      LOGGER.error("Unable to query detached process state: label={}.", watch.label, exception);
      alive = true;
    }

    long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - watch.startedAtNanos);
    if (alive) {
      LOGGER.warn(
          "Detached process is still alive after {}s: label={}, details={}.",
          elapsedSeconds,
          watch.label,
          details(watch));
      return;
    }

    synchronized (lock) {
      watches.remove(watch.label, watch);
    }
    LOGGER.info(
        "Detached process finally exited after {}s: label={}, details={}.",
        elapsedSeconds,
        watch.label,
        details(watch));
  }

  private static String details(Watch watch) {
    try {
      return watch.diagnostics.get();
    } catch (RuntimeException exception) {
      return "<unavailable: " + exception.getMessage() + ">";
    }
  }

  private record Watch(
      String label, BooleanSupplier alive, Supplier<String> diagnostics, long startedAtNanos) {}
}
