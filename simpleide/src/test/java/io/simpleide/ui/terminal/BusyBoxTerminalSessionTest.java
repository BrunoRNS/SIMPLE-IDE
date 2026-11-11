package io.simpleide.ui.terminal;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

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

            shell.onExit().get(5, TimeUnit.SECONDS);
            child.onExit().get(5, TimeUnit.SECONDS);
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

            shell.onExit().get(5, TimeUnit.SECONDS);
            child.onExit().get(5, TimeUnit.SECONDS);
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
}
