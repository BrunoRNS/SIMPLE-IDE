package io.simpleide.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.ThreadMaster;

class LspClientShutdownTest {

  @BeforeAll
  static void initializeThreadManager() {
    ThreadMaster.getInstance().initialize();
  }

  @Test
  void waitsForShutdownToFinishAfterClose() throws Exception {
    LspClient client = new LspClient(
        List.of(javaExecutable(), "-cp", System.getProperty("java.class.path"),
            SleepingProcess.class.getName()),
        null,
        null);
    try {
      await().atMost(10, TimeUnit.SECONDS).until(client::isProcessAlive);

      client.close();

      assertThat(client.awaitShutdown(10, TimeUnit.SECONDS)).isTrue();
      assertThat(client.isProcessAlive()).isFalse();
    } finally {
      client.close();
      client.awaitShutdown(2, TimeUnit.SECONDS);
    }
  }

  private static String javaExecutable() {
    boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
    return Path.of(
        System.getProperty("java.home"),
        "bin",
        windows ? "java.exe" : "java").toString();
  }

  public static final class SleepingProcess {
    public static void main(String[] args) throws InterruptedException {
      Thread.sleep(60_000);
    }
  }
}
