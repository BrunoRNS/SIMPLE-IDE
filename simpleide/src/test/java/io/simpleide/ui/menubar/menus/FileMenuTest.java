package io.simpleide.ui.menubar.menus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class FileMenuTest {

  @Test
  void fileActionsInvokeTheirCallbacksOnTheEdt() throws Exception {
    CountDownLatch callbacksCompleted = new CountDownLatch(3);
    AtomicInteger callbacksInvoked = new AtomicInteger();
    AtomicBoolean callbacksOnEdt = new AtomicBoolean(true);
    Runnable callback = () -> {
      callbacksOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
      callbacksInvoked.incrementAndGet();
      callbacksCompleted.countDown();
    };

    SwingUtilities.invokeAndWait(() -> {
      FileMenu menu = new FileMenu(callback, callback, callback);
      menu.getItem(0).doClick();
      menu.getItem(1).doClick();
      menu.getItem(2).doClick();
    });

    assertThat(callbacksCompleted.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(callbacksInvoked).hasValue(3);
    assertThat(callbacksOnEdt).isTrue();
  }

  @Test
  void rejectsMissingActionCallbacks() {
    assertThatThrownBy(() -> new FileMenu(null, () -> {}, () -> {}))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("createFile");
  }
}
