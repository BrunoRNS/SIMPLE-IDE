package io.simpleide.language.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class LangPlatformTest {

    @Test
    void ciOperatingSystemPropertiesMatchExpectedPlatform() {
        String configuredPlatform = System.getenv("SIMPLEIDE_EXPECTED_PLATFORM");
        assumeTrue(configuredPlatform != null, "CI platform expectations are not configured");
        String[] expectedPlatform = configuredPlatform.split("-", 2);
        assertEquals(2, expectedPlatform.length, "Expected platform must use <os>-<architecture> format");

        String expectedOs = expectedPlatform[0];
        String expectedArchitecture = expectedPlatform[1];
        assertEquals(expectedOs, normalizeOperatingSystem(System.getProperty("os.name")));
        assertEquals(expectedArchitecture, normalizeArchitecture(System.getProperty("os.arch")));

        LangPlatform currentPlatform = LangPlatform.getCurrentPlatform();
        assertEquals(expectedOs, currentPlatform.getOs());
        assertEquals(expectedArchitecture, normalizeArchitecture(currentPlatform.getArchitecture()));
    }

    @Test
    void detectsX64FromMacJdkExecutable() {
        assertEquals(
                "x86_64",
                LangPlatform.architectureFromExecutableDescription("Mach-O 64-bit executable x86_64", "arm64")
        );
    }

    @Test
    void detectsArm64FromMacJdkExecutable() {
        assertEquals(
                "arm64",
                LangPlatform.architectureFromExecutableDescription("Mach-O 64-bit executable arm64", "x86_64")
        );
    }

    @Test
    void usesRuntimeArchitectureForUniversalJdkExecutable() {
        assertEquals(
                "x86_64",
                LangPlatform.architectureFromExecutableDescription(
                        "Mach-O universal binary with 2 architectures: x86_64 arm64",
                        "x86_64"
                )
        );
    }

    private static String normalizeOperatingSystem(String osName) {
        String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("win")) {
            return "windows";
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return "macos";
        }
        if (normalized.contains("linux") || normalized.contains("nix") || normalized.contains("nux")
                || normalized.contains("aix")) {
            return "linux";
        }
        return "unknown";
    }

    private static String normalizeArchitecture(String architecture) {
        String normalized = architecture.toLowerCase(Locale.ROOT);
        if (normalized.contains("aarch64") || normalized.contains("arm64")) {
            return "arm64";
        }
        if (normalized.contains("x86_64") || normalized.contains("amd64") || normalized.contains("x64")) {
            return "x64";
        }
        if (normalized.contains("x86") || normalized.matches("i[3-6]86")) {
            return "x86";
        }
        return "unknown";
    }
}
