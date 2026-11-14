package io.simpleide.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.simpleide.thread.nodes.NodePattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ThreadsTest {

  @Test
  void masterIsInitializedOnFirstUse() {
    assertThat(Threads.master()).isNotNull();
  }

  @Test
  void backgroundRunsTheAction() {
    AtomicBoolean executed = new AtomicBoolean();
    Threads.background(() -> executed.set(true));
    org.awaitility.Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(executed).isTrue());
  }

  @Test
  void onEdtRunsTheActionOnTheEventDispatchThread() throws Exception {
    AtomicBoolean onEdt = new AtomicBoolean();
    Threads.onEdt(() -> onEdt.set(javax.swing.SwingUtilities.isEventDispatchThread()));
    org.awaitility.Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(onEdt).isTrue());
  }

  @Test
  void backgroundAndOnEdtRejectNullActions() {
    assertThatNullPointerException().isThrownBy(() -> Threads.background(null));
    assertThatNullPointerException().isThrownBy(() -> Threads.onEdt(null));
  }

  @Test
  void poolRejectsInvalidArguments() {
    assertThatIllegalArgumentException().isThrownBy(() -> Threads.pool(" ", 1));
    assertThatIllegalArgumentException().isThrownBy(() -> Threads.pool("valid", 0));
    assertThatIllegalArgumentException().isThrownBy(() -> Threads.pool("valid", -3));
  }

  @Test
  void scheduleRejectsInvalidArguments() {
    assertThatNullPointerException().isThrownBy(() -> Threads.schedule(null, 1, TimeUnit.SECONDS));
    assertThatNullPointerException().isThrownBy(() -> Threads.schedule(() -> {}, 1, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Threads.schedule(() -> {}, -1, TimeUnit.SECONDS));
  }

  @Test
  void isRunningReturnsAValueForRegisteredNodes() {
    NodePattern node = (NodePattern) Threads.background(() -> {});
    assertThat(Threads.isRunning(node)).isIn(Boolean.TRUE, Boolean.FALSE);
  }
}
