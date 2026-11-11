package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;

import io.simpleide.language.config.LangPlatform;

class BusyBoxTerminalSessionTest {

    @Test
    void startsAndTerminatesShellForCurrentPlatform() throws Exception {
        LangPlatform platform = LangPlatform.getCurrentPlatform();
        assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform");

        try (BusyBoxTerminalSession session = new BusyBoxTerminalSession(platform)) {
            session.start();
            assertTrue(session.isAlive());
            assertNotNull(session.getConnector());

            session.close();
            assertFalse(session.isAlive());
        }
    }

    @Test
    void runsAnInteractiveCommandThroughTheMacArm64TerminalSession() throws Exception {
        assumeTrue(
            LangPlatform.getCurrentPlatform() == LangPlatform.MACOSAARCH64,
            "Test requires macOS ARM64"
        );

        ExecutorService outputReader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "mac-arm64-terminal-output-test");
            thread.setDaemon(true);
            return thread;
        });
        try (BusyBoxTerminalSession session = new BusyBoxTerminalSession(LangPlatform.MACOSAARCH64)) {
            session.start();
            TtyConnector connector = session.getConnector();
            Future<String> output = outputReader.submit(() -> readUntil(connector, "42"));
            connector.write("printf '%s\\n' \"$((40+2))\"\n");
            assertTrue(
                output.get(10, TimeUnit.SECONDS).contains("42"),
                "The macOS ARM64 terminal did not execute the submitted command."
            );
        } finally {
            outputReader.shutdownNow();
        }
    }

    @Test
    void fallsBackToSystemShellWhenBusyBoxCannotStart() throws Exception {
        LangPlatform platform = LangPlatform.getCurrentPlatform();
        assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform");
        LangPlatform fallbackPlatform = platform == LangPlatform.WINDOWSX64UNICODE
            ? LangPlatform.WINDOWSX86
            : platform;

        @SuppressWarnings("resource")
        BusyBoxTerminalSession session = new BusyBoxTerminalSession(fallbackPlatform);
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        String missingExecutable = Path.of(
            System.getProperty("java.io.tmpdir"),
            "simpleide-missing-busybox-" + UUID.randomUUID()
        ).toString();

        PtyProcess fallback = session.startWithFallback(new String[] {missingExecutable}, environment);
        try {
            assertTrue(fallback.isAlive());
        } finally {
            fallback.destroyForcibly();
            assertTrue(fallback.waitFor(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void keepsMultipleBusyBoxSessionsIndependent() throws Exception {
        LangPlatform platform = LangPlatform.getCurrentPlatform();
        assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform");

        try (
            BusyBoxTerminalSession first = new BusyBoxTerminalSession(platform);
            BusyBoxTerminalSession second = new BusyBoxTerminalSession(platform)
        ) {
            first.start();
            second.start();

            assertTrue(first.isAlive());
            assertTrue(second.isAlive());

            first.close();

            assertFalse(first.isAlive());
            assertTrue(first.isTerminationRequested());
            assertFalse(second.isTerminationRequested());
            assertTrue(second.isAlive());
        }
    }

    @Test
    void forceTerminationAlsoKillsProcessesStartedByTheShell() throws Exception {
        LangPlatform platform = LangPlatform.getCurrentPlatform();
        assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform");

        try (BusyBoxTerminalSession session = new BusyBoxTerminalSession(platform)) {
            session.start();
            session.getConnector().write("sleep 30 &\n");

            ProcessHandle shell = ProcessHandle.of(session.processId()).orElseThrow();
            ProcessHandle child = awaitChild(shell);
            assertTrue(child.isAlive());

            session.forceTerminate();

            awaitProcessExit(shell);
            awaitProcessExit(child);
            assertFalse(session.isAlive());
            assertFalse(child.isAlive());
        }
    }

    @Test
    void gracefullyClosesTheShellAndItsChildProcesses() throws Exception {
        LangPlatform platform = LangPlatform.getCurrentPlatform();
        assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform");

        try (BusyBoxTerminalSession session = new BusyBoxTerminalSession(platform)) {
            session.start();
            session.getConnector().write("sleep 30 &\n");

            ProcessHandle shell = ProcessHandle.of(session.processId()).orElseThrow();
            ProcessHandle child = awaitChild(shell);
            assertTrue(child.isAlive());

            session.closeGracefullyAndWait();

            awaitProcessExit(shell);
            awaitProcessExit(child);
            assertFalse(session.isAlive());
            assertFalse(child.isAlive());
        }
    }

    private static ProcessHandle awaitChild(ProcessHandle process) throws InterruptedException {
        await("The BusyBox shell should create its child process")
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> process.descendants().findFirst().isPresent());
        return process.descendants().findFirst().orElseThrow();
    }

    private static void awaitProcessExit(ProcessHandle process) {
        await("Process " + process.pid() + " should exit")
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> !process.isAlive());
    }

    private static String readUntil(TtyConnector connector, String expected) throws IOException {
        char[] buffer = new char[128];
        StringBuilder output = new StringBuilder();
        int charactersRead;
        while ((charactersRead = connector.read(buffer, 0, buffer.length)) >= 0) {
            output.append(buffer, 0, charactersRead);
            if (output.indexOf(expected) >= 0) {
                return output.toString();
            }
        }
        return output.toString();
    }
}
