package io.simpleide.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.simpleide.thread.nodes.NodePattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WatchDeadTest {

  @Test
  void keepsLoggingUntilTheDetachedProcessExits() {
    ThreadMaster master = new ThreadMaster(rootNode());
    WatchDead watchDead = new WatchDead(master, 10);
    try {
      AtomicBoolean alive = new AtomicBoolean(true);
      AtomicInteger checks = new AtomicInteger();

      watchDead.watchProcess(
          "test process",
          () -> {
            checks.incrementAndGet();
            return alive.get();
          },
          () -> "pid=123");

      assertThat(watchDead.watchCount()).isEqualTo(1);
      await().atMost(5, TimeUnit.SECONDS).until(() -> checks.get() >= 2);

      alive.set(false);
      await().atMost(5, TimeUnit.SECONDS).until(() -> watchDead.watchCount() == 0);

      int checksAfterExit = checks.get();
      await()
          .during(100, TimeUnit.MILLISECONDS)
          .atMost(1, TimeUnit.SECONDS)
          .until(() -> checks.get() == checksAfterExit);
    } finally {
      master.shutdown();
    }
  }

  @Test
  void doesNotDuplicateWatchesForTheSameLabel() {
    ThreadMaster master = new ThreadMaster(rootNode());
    WatchDead watchDead = new WatchDead(master, 10);
    try {
      AtomicBoolean alive = new AtomicBoolean(true);

      watchDead.watchProcess("test process", alive::get, () -> "first");
      watchDead.watchProcess("test process", () -> true, () -> "second");

      assertThat(watchDead.watchCount()).isEqualTo(1);
      alive.set(false);
      await().atMost(5, TimeUnit.SECONDS).until(() -> watchDead.watchCount() == 0);
    } finally {
      master.shutdown();
    }
  }

  private static NodePattern rootNode() {
    return new NodePattern() {
      @Override
      public Runnable onStart() {
        return () -> {};
      }

      @Override
      public Runnable onClose() {
        return null;
      }

      @Override
      public Integer level() {
        return 0;
      }

      @Override
      public boolean isEDT() {
        return false;
      }
    };
  }
}
