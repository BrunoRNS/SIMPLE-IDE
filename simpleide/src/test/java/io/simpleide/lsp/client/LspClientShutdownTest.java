package io.simpleide.lsp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    Optional<Path> javaExecutable = findJavaExecutable();
    assertThat(javaExecutable).as("Java executable").isPresent();
    LspClient client = new LspClient(
        List.of(javaExecutable.get().toString(), "-cp", System.getProperty("java.class.path"),
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

  private static Optional<Path> findJavaExecutable() {
    boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    String exeName = isWindows ? "java.exe" : "java";

    Optional<String> currentCmd = ProcessHandle.current().info().command();
    if (currentCmd.isPresent()) {
      Path cmdPath = Paths.get(currentCmd.get());
      Path fileName = cmdPath.getFileName();
      if (fileName != null && fileName.toString().equalsIgnoreCase(exeName)) {
        Optional<Path> executable = executableFile(cmdPath);
        if (executable.isPresent()) {
          return executable;
        }
      }
    }

    String javaHomeProp = System.getProperty("java.home");
    if (javaHomeProp != null && !javaHomeProp.isEmpty()) {
      Optional<Path> executable = executableFile(Paths.get(javaHomeProp, "bin", exeName));
      if (executable.isPresent()) {
        return executable;
      }
    }

    String envJavaHome = System.getenv("JAVA_HOME");
    if (envJavaHome != null && !envJavaHome.isEmpty()) {
      Optional<Path> executable = executableFile(Paths.get(envJavaHome, "bin", exeName));
      if (executable.isPresent()) {
        return executable;
      }
    }

    String pathEnv = System.getenv("PATH");
    if (pathEnv != null) {
      for (String dir : pathEnv.split(File.pathSeparator)) {
        if (!dir.isBlank()) {
          Optional<Path> executable = executableFile(Paths.get(dir, exeName));
          if (executable.isPresent()) {
            return executable;
          }
        }
      }
    }

    return Optional.empty();
  }

  private static Optional<Path> executableFile(Path path) {
    Path absolutePath = path.toAbsolutePath().normalize();
    return Files.isRegularFile(absolutePath) && Files.isExecutable(absolutePath)
        ? Optional.of(absolutePath)
        : Optional.empty();
  }

  public static final class SleepingProcess {
    public static void main(String[] args) throws InterruptedException {
      Thread.sleep(60_000);
    }
  }
}
