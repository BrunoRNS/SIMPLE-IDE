package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;

import io.simpleide.language.config.LangPlatform;

class BusyBoxTerminalSessionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusyBoxTerminalSessionTest.class);
    private static final long MAC_ARM_PROCESS_START_TIMEOUT_SECONDS = 15;
    private static final long MAC_ARM_PROCESS_EXIT_TIMEOUT_SECONDS = 60;

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
    void fallbackShellRunsCommandsThroughAnInteractivePty() throws Exception {
        LangPlatform platform = LangPlatform.getCurrentPlatform();
        assumeTrue(platform != LangPlatform.UNKNOWN, "Test requires a supported platform.");
        assumeTrue(
            !"windows".equals(platform.getOs()),
            "This test requires a POSIX system shell."
        );

        @SuppressWarnings("resource")
        BusyBoxTerminalSession session = new BusyBoxTerminalSession(platform);
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        String missingExecutable = Path.of(
            System.getProperty("java.io.tmpdir"),
            "simpleide-missing-busybox-" + UUID.randomUUID()
        ).toString();
        PtyProcess fallback = session.startWithFallback(new String[] {missingExecutable}, environment);
        ProcessTtyConnector connector = new ProcessTtyConnector(fallback, StandardCharsets.UTF_8) {

            @Override
            public void resize(TermSize termSize) {
            }

            @Override
            public String getName() {
                return "fallback-pty-test";
            }
        };
        ExecutorService outputReader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "fallback-pty-output-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            assertTrue(session.processDiagnostics().contains("systemShellFallback=true"));
            String fallbackShellCommand = ProcessHandle.of(fallback.pid())
                .flatMap(handle -> handle.info().command())
                .orElse("unknown");
            String fallbackShellName = Path.of(fallbackShellCommand).getFileName().toString();
            assertTrue(
                Set.of("sh", "dash", "bash", "zsh", "ksh").contains(fallbackShellName),
                "Fallback shell command did not resolve to /sh: pid=" + fallback.pid()
                    + ", command=" + fallbackShellCommand
                    + ", diagnostics=" + session.processDiagnostics()
            );
            Future<String> output = outputReader.submit(() -> readUntil(connector, "PTY_OK"));
            connector.write(
                "if test -t 0 && test -t 1; then "
                    + "printf '\\120\\124\\131\\137\\117\\113\\n'; "
                    + "else printf '\\120\\124\\131\\137\\116\\117\\n'; fi\n"
            );
            assertTrue(
                output.get(10, TimeUnit.SECONDS).contains("PTY_OK"),
                "The /bin/sh fallback did not execute a command through the PTY."
            );
        } finally {
            outputReader.shutdownNow();
            connector.close();
            ProcessTreeTerminator.terminate(fallback, platform);
            assertTrue(fallback.waitFor(5, TimeUnit.SECONDS), "Fallback shell did not exit after cleanup.");
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void macArm64ForceTerminationStopsBackgroundChildrenFromPty() throws Exception {
        assertMacArm64TerminationStopsBackgroundChildren(true);
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void macArm64GracefulCloseStopsBackgroundChildrenFromPty() throws Exception {
        assertMacArm64TerminationStopsBackgroundChildren(false);
    }

    private static void assertMacArm64TerminationStopsBackgroundChildren(boolean force) throws Exception {
        assumeTrue(
            !System.getProperty("os.name").toLowerCase().contains("win"),
            "This test requires a POSIX system shell."
        );

        LOGGER.info(
            "Starting macOS ARM64 PTY termination diagnostic: mode={}, os={}, arch={}, java={}.",
            force ? "force" : "graceful",
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            System.getProperty("java.version")
        );
        try (BusyBoxTerminalSession session = new BusyBoxTerminalSession(LangPlatform.MACOSAARCH64)) {
            try {
                session.start();
            } catch (IOException | RuntimeException | LinkageError exception) {
                LOGGER.error(
                    "Terminal shell failed to start: mode={}, sessionState={}.",
                    force ? "force" : "graceful",
                    session.processDiagnostics(),
                    exception
                );
                throw exception;
            }
            assertTrue(session.isAlive(), "Terminal shell did not remain alive after startup: "
                + session.processDiagnostics());
            assertNotNull(session.getConnector(), "Terminal shell started without a PTY connector.");
            ProcessHandle shell = ProcessHandle.of(session.processId()).orElseThrow(() ->
                new AssertionError("Terminal process handle missing after startup: " + session.processDiagnostics())
            );
            LOGGER.info(
                "Terminal shell started: mode={}, sessionState={}, shell={}.",
                force ? "force" : "graceful",
                session.processDiagnostics(),
                describeProcess(shell)
            );

            try (PtyOutputCapture output = new PtyOutputCapture(session.getConnector())) {
                session.getConnector().write("sleep 30 & printf 'SIMPLEIDE_CHILD_STARTED\\n'\n");
                ProcessHandle child;
                try {
                    child = awaitChild(shell);
                    output.awaitContains("SIMPLEIDE_CHILD_STARTED");
                } catch (AssertionError | ConditionTimeoutException exception) {
                    LOGGER.error(
                        "Background child did not start as expected: sessionState={}, shell={}, "
                            + "descendants={}, ptyOutput={}.",
                        session.processDiagnostics(),
                        describeProcess(shell),
                        describeDescendants(shell),
                        output.snapshot(),
                        exception
                    );
                    throw exception;
                }
                assertTrue(child.isAlive(), "Background child was not alive before close: "
                    + describeProcess(child) + "; PTY output=" + output.snapshot());
                LOGGER.info(
                    "Background child started: mode={}, sessionState={}, shell={}, child={}, ptyOutput={}.",
                    force ? "force" : "graceful",
                    session.processDiagnostics(),
                    describeProcess(shell),
                    describeProcess(child),
                    output.snapshot()
                );

                long closeStartedAt = System.nanoTime();
                LOGGER.info(
                    "Attempting {} close: sessionState={}, shell={}, child={}, ptyOutputBeforeClose={}.",
                    force ? "forced" : "graceful",
                    session.processDiagnostics(),
                    describeProcess(shell),
                    describeProcess(child),
                    output.snapshot()
                );
                if (force) {
                    session.forceTerminate();
                } else {
                    session.closeGracefullyAndWait();
                }
                String ptyOutputAfterClose = output.awaitEndOrSnapshot(10);
                LOGGER.info(
                    "Close attempt returned after {}ms: mode={}, sessionState={}, shell={}, child={}, "
                        + "ptyOutputAfterClose={}.",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStartedAt),
                    force ? "force" : "graceful",
                    session.processDiagnostics(),
                    describeProcess(shell),
                    describeProcess(child),
                    ptyOutputAfterClose
                );

                awaitProcessExit(shell, MAC_ARM_PROCESS_EXIT_TIMEOUT_SECONDS, "terminal shell", output.snapshot());
                awaitProcessExit(child, MAC_ARM_PROCESS_EXIT_TIMEOUT_SECONDS, "background child", output.snapshot());
                assertFalse(session.isAlive(), "Terminal shell still alive after close: "
                    + session.processDiagnostics() + "; PTY output=" + output.snapshot());
                assertFalse(child.isAlive(), "Background child still alive after close: "
                    + describeProcess(child) + "; PTY output=" + output.snapshot());
                LOGGER.info(
                    "Verified macOS ARM64 close: mode={}, sessionState={}, shell={}, child={}, ptyOutput={}.",
                    force ? "force" : "graceful",
                    session.processDiagnostics(),
                    describeProcess(shell),
                    describeProcess(child),
                    ptyOutputAfterClose
                );
            }
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
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
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
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
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
        AtomicReference<ProcessHandle> child = new AtomicReference<>();
        try {
            await("The shell should create its child process: " + describeProcess(process))
                .atMost(MAC_ARM_PROCESS_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    try (var descendants = process.descendants()) {
                        descendants.findFirst().ifPresent(child::set);
                        return child.get() != null;
                    }
                });
        } catch (ConditionTimeoutException exception) {
            throw new AssertionError(
                "Shell did not create a child within " + MAC_ARM_PROCESS_START_TIMEOUT_SECONDS
                    + " seconds. shell=" + describeProcess(process)
                    + "; descendants=" + describeDescendants(process),
                exception
            );
        }
        return child.get();
    }

    private static void awaitProcessExit(ProcessHandle process) {
        awaitProcessExit(process, MAC_ARM_PROCESS_EXIT_TIMEOUT_SECONDS, "process", "no PTY output captured");
    }

    private static void awaitProcessExit(
        ProcessHandle process,
        long timeoutSeconds,
        String role,
        String ptyOutput
    ) {
        try {
            await(role + " should exit: " + describeProcess(process))
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> !process.isAlive());
        } catch (ConditionTimeoutException exception) {
            String diagnostics = "role=" + role
                + ", timeoutSeconds=" + timeoutSeconds
                + ", process=" + describeProcess(process)
                + ", descendants=" + describeDescendants(process)
                + ", ptyOutput=" + ptyOutput;
            LOGGER.error("Process did not exit before the test timeout: {}.", diagnostics, exception);
            throw new AssertionError("Process did not exit before the test timeout: " + diagnostics, exception);
        }
    }

    private static String describeProcess(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        return "pid=" + process.pid()
            + ", alive=" + process.isAlive()
            + ", parentPid=" + process.parent().map(ProcessHandle::pid).orElse(-1L)
            + ", command=" + info.command().orElse("unknown")
            + ", arguments=" + String.join(" ", info.arguments().orElse(new String[0]));
    }

    private static String describeDescendants(ProcessHandle process) {
        try (var descendants = process.descendants()) {
            return descendants.map(BusyBoxTerminalSessionTest::describeProcess).toList().toString();
        }
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

    private static final class PtyOutputCapture implements AutoCloseable {

        private final StringBuilder output = new StringBuilder();
        private final ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "mac-arm64-pty-diagnostic-reader");
            thread.setDaemon(true);
            return thread;
        });
        private final Future<?> outputRead;

        private PtyOutputCapture(TtyConnector connector) {
            outputRead = reader.submit(() -> {
                char[] buffer = new char[256];
                try {
                    int count;
                    while ((count = connector.read(buffer, 0, buffer.length)) >= 0) {
                        if (count > 0) {
                            synchronized (output) {
                                output.append(buffer, 0, count);
                            }
                        }
                    }
                    LOGGER.info("PTY output stream reached EOF: output={}.", snapshot());
                } catch (IOException exception) {
                    LOGGER.warn("PTY output capture stopped with an I/O error: output={}.", snapshot(), exception);
                }
            });
        }

        private void awaitContains(String expected) {
            try {
                await("PTY output should contain " + expected)
                    .atMost(MAC_ARM_PROCESS_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .until(() -> snapshot().contains(expected));
            } catch (ConditionTimeoutException exception) {
                throw new AssertionError(
                    "PTY output did not contain " + expected + " within "
                        + MAC_ARM_PROCESS_START_TIMEOUT_SECONDS + " seconds; captured=" + snapshot(),
                    exception
                );
            }
        }

        private String awaitEndOrSnapshot(long timeoutSeconds) throws InterruptedException {
            try {
                outputRead.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                return "PTY reader did not reach EOF within " + timeoutSeconds
                    + " seconds; captured=" + snapshot();
            } catch (ExecutionException exception) {
                return "PTY reader failed: " + exception.getCause() + "; captured=" + snapshot();
            }
            return snapshot();
        }

        private String snapshot() {
            synchronized (output) {
                return output.toString();
            }
        }

        @Override
        public void close() {
            reader.shutdownNow();
        }
    }
}
