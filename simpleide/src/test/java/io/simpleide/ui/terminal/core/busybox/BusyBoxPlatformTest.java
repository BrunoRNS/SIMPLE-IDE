package io.simpleide.ui.terminal.core.busybox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.utils.ResourceUtil;

class BusyBoxPlatformTest {

    private static final String EXPECTED_OUTPUT = "simpleide-busybox-test";

    @Test
    void runsLinuxX86BusyBox() throws Exception {
        assumePlatform("linux", "x86", "i386", "i486", "i586", "i686");
        runBusyBox("linux/linux-x86/bin/busybox", false);
    }

    @Test
    void runsLinuxX64BusyBox() throws Exception {
        assumePlatform("linux", "amd64", "x86_64", "x64");
        runBusyBox("linux/linux-x64/bin/busybox", false);
    }

    @Test
    void runsLinuxArm64BusyBox() throws Exception {
        assumePlatform("linux", "aarch64", "arm64");
        runBusyBox("linux/linux-arm64/bin/busybox", false);
    }

    @Test
    void runsMacIntelBusyBox() throws Exception {
        assumePlatform("mac", "amd64", "x86_64", "x64");
        runBusyBox("macos/macos-intel/bin/busybox", false);
    }

    @Test
    void runsMacArm64BusyBox() throws Exception {
        assumePlatform("mac", "aarch64", "arm64");
        runBusyBox("macos/macos-arm64/bin/busybox", false);
    }

    @Test
    void runsWindowsX86BusyBox() throws Exception {
        assumePlatform("win", "x86", "i386", "i486", "i586", "i686");
        runBusyBox("windows/windows-x86/bin/busybox.exe", true);
    }

    @Test
    void runsWindowsX64BusyBox() throws Exception {
        assumePlatform("win", "amd64", "x86_64", "x64");
        runBusyBox("windows/windows-x64-unicode/bin/busybox.exe", true);
    }

    @Test
    void runsWindowsArm64BusyBox() throws Exception {
        assumePlatform("win", "aarch64", "arm64");
        runBusyBox("windows/windows-arm64/bin/busybox.exe", true);
    }

    private static void assumePlatform(String operatingSystem, String... architectures) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        assumeTrue(osName.contains(operatingSystem), "Test requires " + operatingSystem);
        assumeTrue(
            java.util.Arrays.asList(architectures).contains(architecture),
            "Test requires one of " + java.util.Arrays.toString(architectures) + "; found " + architecture
        );
    }

    private static void runBusyBox(String resourcePath, boolean windows) throws Exception {
        Path resource = ResourceUtil.getResourcePath(
            BusyBoxPlatformTest.class,
            resourcePath.split("/")
        );
        Path tempDirectory = Files.createTempDirectory("simpleide-busybox-");
        Path executable = tempDirectory.resolve(windows ? "busybox.exe" : "busybox");
        try {
            Files.copy(resource, executable);
            if (!windows) {
                assertTrue(executable.toFile().setExecutable(true), "BusyBox should be executable");
            }

            Process process = new ProcessBuilder(
                executable.toString(),
                "echo",
                EXPECTED_OUTPUT
            ).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("BusyBox did not finish within 10 seconds: " + resourcePath);
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(0, process.exitValue(), output);
            assertEquals(EXPECTED_OUTPUT, output);
        } finally {
            Files.deleteIfExists(executable);
            Files.deleteIfExists(tempDirectory);
        }
    }
}
