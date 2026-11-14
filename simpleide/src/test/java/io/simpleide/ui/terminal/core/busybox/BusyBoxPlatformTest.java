package io.simpleide.ui.terminal.core.busybox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import io.simpleide.language.config.LangPlatform;
import io.simpleide.ui.terminal.Pty4jCompatibility;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BusyBoxPlatformTest {

  private static final String EXPECTED_OUTPUT = "simpleide-busybox-test";

  @Test
  void mapsEverySupportedLangPlatformToItsBundledBusyBox() throws Exception {
    assertEquals(
        "windows/windows-x86/bin/busybox.exe",
        BusyBoxPlatform.getResourcePath(LangPlatform.WINDOWSX86));
    assertEquals(
        "windows/windows-x64-unicode/bin/busybox.exe",
        BusyBoxPlatform.getResourcePath(LangPlatform.WINDOWSX64UNICODE));
    assertEquals(
        "windows/windows-arm64/bin/busybox.exe",
        BusyBoxPlatform.getResourcePath(LangPlatform.WINDOWSARM64));
    assertEquals(
        "linux/linux-x86/bin/busybox", BusyBoxPlatform.getResourcePath(LangPlatform.LINUXX86));
    assertEquals(
        "linux/linux-x64/bin/busybox", BusyBoxPlatform.getResourcePath(LangPlatform.LINUXX64));
    assertEquals(
        "linux/linux-arm64/bin/busybox", BusyBoxPlatform.getResourcePath(LangPlatform.LINUXARM64));
    assertEquals(
        "macos/macos-intel/bin/busybox", BusyBoxPlatform.getResourcePath(LangPlatform.MACOSINTEL));
    assertEquals(
        "macos/macos-arm64/bin/busybox",
        BusyBoxPlatform.getResourcePath(LangPlatform.MACOSAARCH64));
    assertThatThrownBy(() -> BusyBoxPlatform.getResourcePath(LangPlatform.UNKNOWN))
        .isInstanceOf(java.io.IOException.class);
  }

  @Test
  void selectsTheWindowsX86BusyBoxBeforeWindows10() {
    assertEquals(
        LangPlatform.WINDOWSX86,
        BusyBoxPlatform.getCompatiblePlatform(LangPlatform.WINDOWSX64UNICODE, "6.3"));
    assertEquals(
        LangPlatform.WINDOWSX86,
        BusyBoxPlatform.getCompatiblePlatform(LangPlatform.WINDOWSX64UNICODE, "unknown"));
    assertEquals(
        LangPlatform.WINDOWSX64UNICODE,
        BusyBoxPlatform.getCompatiblePlatform(LangPlatform.WINDOWSX64UNICODE, "10.0.19045"));
    assertEquals(
        LangPlatform.WINDOWSARM64,
        BusyBoxPlatform.getCompatiblePlatform(LangPlatform.WINDOWSARM64, "6.3"));
  }

  @Test
  void runsLinuxX86BusyBox() throws Exception {
    assumePlatform("linux", "x86", "i386", "i486", "i586", "i686");
    runBusyBox(LangPlatform.LINUXX86);
  }

  @Test
  void runsLinuxX64BusyBox() throws Exception {
    assumePlatform("linux", "amd64", "x86_64", "x64");
    runBusyBox(LangPlatform.LINUXX64);
  }

  @Test
  void runsLinuxArm64BusyBox() throws Exception {
    assumePlatform("linux", "aarch64", "arm64");
    runBusyBox(LangPlatform.LINUXARM64);
  }

  @Test
  void runsMacIntelBusyBox() throws Exception {
    assumePlatform("mac", "amd64", "x86_64", "x64");
    runBusyBox(LangPlatform.MACOSINTEL);
  }

  @Test
  void runsMacArm64BusyBox() throws Exception {
    assumePlatform("mac", "aarch64", "arm64");
    runBusyBox(LangPlatform.MACOSAARCH64);
  }

  @Test
  void runsWindowsX86BusyBox() throws Exception {
    assumePlatform("win", "x86", "i386", "i486", "i586", "i686");
    runBusyBox(LangPlatform.WINDOWSX86);
  }

  @Test
  void runsWindowsX64BusyBox() throws Exception {
    assumePlatform("win", "amd64", "x86_64", "x64");
    runBusyBox(LangPlatform.WINDOWSX64UNICODE);
  }

  @Test
  void runsWindowsArm64BusyBox() throws Exception {
    assumePlatform("win", "aarch64", "arm64");
    runBusyBox(LangPlatform.WINDOWSARM64);
  }

  @Test
  void startsEmbeddedBusyBoxThroughPty4j() throws Exception {
    LangPlatform platform = LangPlatform.getCurrentPlatform();
    assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform");
    try (BusyBoxExecutable executable = BusyBoxPlatform.extract(platform)) {
      PtyProcessBuilder builder =
          new PtyProcessBuilder()
              .setCommand(new String[] {executable.getPath().toString(), "echo", EXPECTED_OUTPUT})
              .setEnvironment(Map.copyOf(System.getenv()));
      Pty4jCompatibility.setSpawnProcessUsingJdkOnMacIntel(
          builder, platform != LangPlatform.MACOSINTEL);
      PtyProcess process =
          builder
              .setUnixOpenTtyToPreserveOutputAfterTermination(
                  platform == LangPlatform.MACOSINTEL || platform == LangPlatform.MACOSAARCH64)
              .start();
      ExecutorService outputReader =
          Executors.newSingleThreadExecutor(
              task -> {
                Thread thread = new Thread(task, "busybox-pty-test-output");
                thread.setDaemon(true);
                return thread;
              });
      Future<String> output =
          outputReader.submit(() -> readUntil(process.getInputStream(), EXPECTED_OUTPUT));
      try {
        String actualOutput = output.get(10, TimeUnit.SECONDS);

        assertTrue(
            actualOutput.contains(EXPECTED_OUTPUT),
            () ->
                "BusyBox output did not contain the expected text. Actual output:\n"
                    + actualOutput);
      } finally {
        process.destroyForcibly();

        boolean processStopped = process.waitFor(10, TimeUnit.SECONDS);

        outputReader.shutdownNow();
        boolean readerStopped = outputReader.awaitTermination(6, TimeUnit.SECONDS);

        assertTrue(processStopped, "PTY BusyBox process did not stop after test cleanup.");
        assertTrue(readerStopped, "Output reader did not stop after test cleanup.");
      }
    }
  }

  @SuppressWarnings("unused")
  private static String readUntil(InputStream input, String expected) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[128];
    while (true) {
      int count = input.read(buffer);
      if (count < 0) {
        return output.toString(StandardCharsets.UTF_8);
      }
      output.write(buffer, 0, count);
      String text = output.toString(StandardCharsets.UTF_8);
      if (text.contains(expected)) {
        return text;
      }
    }
  }

  private static void assumePlatform(String operatingSystem, String... architectures) {
    String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String architecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
    assumeTrue(osName.contains(operatingSystem), "Test requires " + operatingSystem);
    assumeTrue(
        java.util.Arrays.asList(architectures).contains(architecture),
        "Test requires one of "
            + java.util.Arrays.toString(architectures)
            + "; found "
            + architecture);
  }

  private static void runBusyBox(LangPlatform platform) throws Exception {
    try (BusyBoxExecutable executable = BusyBoxPlatform.extract(platform)) {
      Process process =
          new ProcessBuilder(executable.getPath().toString(), "echo", EXPECTED_OUTPUT)
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new AssertionError("BusyBox did not finish within 10 seconds: " + platform);
      }

      String output =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      assertEquals(0, process.exitValue(), output);
      assertEquals(EXPECTED_OUTPUT, output);
    }
  }
}
