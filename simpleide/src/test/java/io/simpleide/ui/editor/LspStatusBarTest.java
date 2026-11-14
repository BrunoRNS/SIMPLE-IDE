package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.simpleide.lsp.LspStatus;
import io.simpleide.thread.ThreadMaster;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LspStatusBarTest {

  @Test
  void updatesStatusThroughThreadMasterEdtQueue() throws Exception {
    ThreadMaster threadMaster = ThreadMaster.getInstance();
    threadMaster.initialize();
    AtomicReference<LspStatusBar> statusBar = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> statusBar.set(new LspStatusBar()));

    statusBar
        .get()
        .updateStatus(new LspStatus("java@workspace", "jdtls", LspStatus.State.STARTING));

    CountDownLatch rendered = new CountDownLatch(1);
    threadMaster.executeOnEDT(
        () -> {
          assertThat(((JLabel) statusBar.get().getComponent(0)).getText())
              .isEqualTo("LSP: Starting jdtls...");
          rendered.countDown();
        });

    assertTrue(rendered.await(10, TimeUnit.SECONDS));
  }
}
