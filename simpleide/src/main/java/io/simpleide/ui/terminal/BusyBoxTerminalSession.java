package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.ui.terminal.core.busybox.BusyBoxExecutable;
import io.simpleide.ui.terminal.core.busybox.BusyBoxPlatform;

public final class BusyBoxTerminalSession implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusyBoxTerminalSession.class);
    private static final int TERMINAL_COLUMNS = 80;
    private static final int TERMINAL_ROWS = 24;
    private static final long STARTUP_PROBE_MILLIS = 250;
    private static final long GRACEFUL_CLOSE_TIMEOUT_SECONDS = 2;
    private static final Set<BusyBoxTerminalSession> ACTIVE_SESSIONS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SHUTDOWN_STARTED.set(true);
            for (BusyBoxTerminalSession session : ACTIVE_SESSIONS) {
                session.forceTerminate();
                session.close(true);
            }
        }, "simpleide-busybox-shutdown"));
    }

    private final LangPlatform platform;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean terminationRequested = new AtomicBoolean();
    private volatile BusyBoxExecutable executable;
    private volatile PtyProcess process;
    private volatile BusyBoxTtyConnector connector;
    private volatile boolean systemShellFallback;
    private volatile boolean busyBoxX86Compatibility;

    BusyBoxTerminalSession(LangPlatform platform) {
        this.platform = platform;
    }

    void start() throws IOException {
        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("BusyBox startup was interrupted before the PTY process was created.");
            }

            Map<String, String> environment = new HashMap<>(System.getenv());
            environment.put("TERM", "xterm-256color");
            LangPlatform busyBoxPlatform = BusyBoxPlatform.getCompatiblePlatform(
                platform,
                System.getProperty("os.version")
            );
            if (busyBoxPlatform != platform) {
                busyBoxX86Compatibility = true;
                LOGGER.info(
                    "Windows version {} is below Windows 10; using the x86 BusyBox compatibility binary.",
                    System.getProperty("os.version")
                );
            }
            String[] command = null;
            Throwable extractionFailure = null;
            try {
                executable = BusyBoxPlatform.extract(busyBoxPlatform);
                command = busyBoxPlatform == LangPlatform.MACOSAARCH64
                    ? new String[] {executable.getPath().toString(), "sh", "-i"}
                    : new String[] {executable.getPath().toString(), "sh"};
            } catch (IOException | RuntimeException | LinkageError exception) {
                extractionFailure = exception;
            }
            process = startWithFallback(command, environment, extractionFailure, busyBoxPlatform);
            LOGGER.info("Terminal shell started: {}.", processDiagnostics());

            ACTIVE_SESSIONS.add(this);
            if (SHUTDOWN_STARTED.get()) {
                close(true);
                return;
            }
            connector = new BusyBoxTtyConnector(
                process,
                StandardCharsets.UTF_8,
                getConnectorName()
            );
        } catch (IOException | RuntimeException | LinkageError exception) {
            LOGGER.error("Terminal shell startup failed: {}.", processDiagnostics(), exception);
            cleanupFailedStart(exception);
            throw exception;
        }
    }

    boolean isAlive() {
        PtyProcess activeProcess = process;
        return activeProcess != null && isProcessAlive(activeProcess);
    }

    long processId() {
        PtyProcess activeProcess = process;
        if (activeProcess == null) {
            throw new IllegalStateException("BusyBox has not been started.");
        }
        return activeProcess.pid();
    }

    TtyConnector getConnector() {
        return connector;
    }

    CompletableFuture<Process> onExit() {
        PtyProcess activeProcess = process;
        if (activeProcess == null) {
            throw new IllegalStateException("BusyBox has not been started.");
        }
        return activeProcess.onExit();
    }

    boolean isTerminationRequested() {
        return terminationRequested.get();
    }

    String processDiagnostics() {
        PtyProcess activeProcess = process;
        String details = "platform=" + platform
            + ", os=" + System.getProperty("os.name")
            + ", arch=" + System.getProperty("os.arch")
            + ", java=" + System.getProperty("java.version")
            + ", busyBoxX86Compatibility=" + busyBoxX86Compatibility
            + ", systemShellFallback=" + systemShellFallback;
        if (platform == LangPlatform.MACOSAARCH64) {
            details += ", osVersion=" + System.getProperty("os.version");
        }
        if (activeProcess == null) {
            return details + ", process=not-started";
        }

        boolean ptyAlive = isProcessAlive(activeProcess);
        String state = details
            + ", pid=" + activeProcess.pid()
            + ", ptyAlive=" + ptyAlive
            + ", processHandleAlive=" + isProcessHandleAlive(activeProcess.pid());
        if (!ptyAlive) {
            try {
                state += ", exitCode=" + activeProcess.exitValue();
            } catch (IllegalThreadStateException exception) {
                state += ", exitCode=unavailable";
            }
        }
        return state;
    }

    static final class FallbackStartupException extends IOException {

        private FallbackStartupException(String message, Throwable busyBoxFailure, Throwable fallbackFailure) {
            super(message, fallbackFailure);
            addSuppressed(busyBoxFailure);
        }
    }

    void forceTerminate() {
        terminationRequested.set(true);
        PtyProcess activeProcess = process;
        if (activeProcess == null || !isProcessAlive(activeProcess)) {
            LOGGER.debug("Skipping forced terminal shutdown: {}", processDiagnostics());
            return;
        }
        LOGGER.info("Forcing terminal process-tree shutdown: {}", processDiagnostics());
        try {
            ProcessTreeTerminator.terminate(activeProcess, platform);
            LOGGER.info("Forced terminal shutdown returned: {}", processDiagnostics());
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to force the BusyBox process to exit: {}", processDiagnostics(), exception);
        }
    }

    public void forceTerminateAndWait() {
        forceTerminate();
        PtyProcess activeProcess = process;
        if (activeProcess == null || !isProcessAlive(activeProcess)) {
            return;
        }

        boolean interrupted = Thread.interrupted();
        try {
            if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(activeProcess)) {
                ProcessTreeTerminator.terminate(activeProcess, platform);
                if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(activeProcess)) {
                    LOGGER.error(
                        "BusyBox process {} is still alive after forced termination.",
                        activeProcess.pid()
                    );
                }
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            LOGGER.warn(
                "Interrupted while closing terminal; falling back to forced termination: {}.",
                processDiagnostics(),
                exception
            );
            forceTerminate();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void closeGracefullyAndWait() {
        terminationRequested.set(true);
        PtyProcess activeProcess = process;
        if (activeProcess == null) {
            LOGGER.info("Skipping graceful terminal shutdown because no process was started: {}.", processDiagnostics());
            return;
        }
        if (!isProcessAlive(activeProcess)) {
            LOGGER.info("Skipping graceful terminal shutdown because the process already exited: {}.", processDiagnostics());
            return;
        }

        List<ProcessHandle> descendants = ProcessTreeTerminator.captureDescendants(activeProcess);
        LOGGER.info(
            "Attempting graceful terminal shutdown: {}; captured descendants={}",
            processDiagnostics(),
            processTreeDiagnostics(descendants)
        );
        if (platform == LangPlatform.MACOSAARCH64) {
            LOGGER.info(
                "Stopping captured macOS ARM64 child processes before sending EOF: session={}, descendants={}",
                activeProcess.pid(),
                processTreeDiagnostics(descendants)
            );
            ProcessTreeTerminator.terminateMacArm64Descendants(activeProcess, descendants);
        }
        boolean interrupted = Thread.interrupted();
        try {
            BusyBoxTtyConnector activeConnector = connector;
            if (activeConnector != null) {
                try {
                    LOGGER.info(
                        "Sending {} to terminal session {} through connector {}.",
                        "windows".equals(platform.getOs()) && systemShellFallback ? "Ctrl-Z" : "EOF",
                        activeProcess.pid(),
                        activeConnector.getName()
                    );
                    activeConnector.write(
                        "windows".equals(platform.getOs()) && systemShellFallback
                            ? "\u001A\r\n"
                            : "\u0004"
                    );
                } catch (IOException exception) {
                    LOGGER.warn("Unable to send EOF to terminal session {}.", activeProcess.pid(), exception);
                }
            }

            if (activeProcess.waitFor(GRACEFUL_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                ProcessTreeTerminator.terminateCapturedDescendants(descendants, platform);
                awaitCapturedDescendants(descendants, activeProcess.pid());
                LOGGER.info(
                    "Terminal session exited after EOF: {}; remaining captured descendants={}",
                    processDiagnostics(),
                    processTreeDiagnostics(descendants)
                );
                return;
            }

            LOGGER.info(
                "Terminal session did not exit within {} seconds after EOF; terminating its process tree: {}; "
                    + "remaining descendants={}",
                GRACEFUL_CLOSE_TIMEOUT_SECONDS,
                processDiagnostics(),
                processTreeDiagnostics(descendants)
            );
            ProcessTreeTerminator.terminate(activeProcess, platform);
            if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(activeProcess)) {
                LOGGER.error(
                    "Terminal session remained alive after process-tree termination: {}; descendants={}",
                    processDiagnostics(),
                    processTreeDiagnostics(descendants)
                );
            } else {
                LOGGER.info(
                    "Terminal session exited after process-tree termination: {}; descendants={}",
                    processDiagnostics(),
                    processTreeDiagnostics(descendants)
                );
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            forceTerminate();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        closeGracefullyAndWait();
        close(false);
    }

    void close(boolean force) {
        terminationRequested.set(true);
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        PtyProcess activeProcess = process;
        boolean interrupted = Thread.interrupted();
        try {
            if (activeProcess != null && isProcessAlive(activeProcess)) {
                if (force) {
                    forceTerminate();
                } else {
                    activeProcess.destroy();
                }
                if (isProcessAlive(activeProcess) && !activeProcess.waitFor(2, TimeUnit.SECONDS)) {
                    forceTerminate();
                    activeProcess.waitFor(2, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            forceTerminate();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to stop the BusyBox process during cleanup.", exception);
            forceTerminate();
        } finally {
            try {
                BusyBoxTtyConnector activeConnector = connector;
                if (activeConnector != null) {
                    activeConnector.close();
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to close the BusyBox PTY connector.", exception);
            }
            try {
                BusyBoxExecutable extractedExecutable = executable;
                if (extractedExecutable != null) {
                    extractedExecutable.close();
                }
            } catch (IOException exception) {
                LOGGER.error("Unable to remove the extracted BusyBox executable.", exception);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                ACTIVE_SESSIONS.remove(this);
            }
        }
    }

    private boolean isProcessAlive(PtyProcess activeProcess) {
        return activeProcess.isAlive();
    }

    private boolean isProcessHandleAlive(long processId) {
        try {
            Optional<ProcessHandle> handle = ProcessHandle.of(processId);
            return handle.map(ProcessHandle::isAlive).orElse(false);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to query BusyBox process handle state for PID {}.", processId, exception);
            return false;
        }
    }

    private void awaitCapturedDescendants(List<ProcessHandle> descendants, long sessionPid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        for (ProcessHandle descendant : descendants) {
            while (descendant.isAlive()) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    LOGGER.warn(
                        "Child process from terminal session did not exit after termination: session={}, child={}",
                        sessionPid,
                        processTreeDiagnostics(List.of(descendant))
                    );
                    return;
                }
                try {
                    TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(20)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static String processTreeDiagnostics(List<ProcessHandle> processes) {
        return processes.stream()
            .map(process -> {
                ProcessHandle.Info info = process.info();
                return "pid=" + process.pid()
                    + ", alive=" + process.isAlive()
                    + ", parentPid=" + process.parent().map(ProcessHandle::pid).orElse(-1L)
                    + ", command=" + info.command().orElse("unknown")
                    + ", arguments=" + String.join(" ", info.arguments().orElse(new String[0]));
            })
            .toList()
            .toString();
    }

    private PtyProcess startProcess(String[] command, Map<String, String> environment) throws IOException {
        PtyProcessBuilder builder = new PtyProcessBuilder()
            .setCommand(command)
            .setEnvironment(environment)
            .setInitialColumns(TERMINAL_COLUMNS)
            .setInitialRows(TERMINAL_ROWS);
        Pty4jCompatibility.setSpawnProcessUsingJdkOnMacIntel(
            builder,
            platform != LangPlatform.MACOSINTEL
        );
        return builder
            .setUnixOpenTtyToPreserveOutputAfterTermination(platform == LangPlatform.MACOSAARCH64)
            .start();
    }

    PtyProcess startWithFallback(String[] command, Map<String, String> environment) throws IOException {
        LangPlatform busyBoxPlatform = BusyBoxPlatform.getCompatiblePlatform(
            platform,
            System.getProperty("os.version")
        );
        return startWithFallback(command, environment, null, busyBoxPlatform);
    }

    private PtyProcess startWithFallback(
        String[] command,
        Map<String, String> environment,
        Throwable initialBusyBoxFailure,
        LangPlatform busyBoxPlatform
    ) throws IOException {
        Throwable busyBoxFailure = initialBusyBoxFailure;
        if (busyBoxFailure == null) {
            PtyProcess candidate = null;
            try {
                candidate = startProcess(command, environment);
                if (!exitedDuringStartup(candidate)) {
                    return candidate;
                }
                busyBoxFailure = new IOException(
                    "BusyBox shell exited during startup: " + processDiagnostics(candidate)
                );
            } catch (IOException | RuntimeException | LinkageError exception) {
                terminateFailedProcess(candidate, exception);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException(
                        "BusyBox startup was interrupted; the system fallback was not started.",
                        exception
                    );
                }
                busyBoxFailure = exception;
            }
        }

        if (platform == LangPlatform.WINDOWSX64UNICODE
            && busyBoxPlatform == LangPlatform.WINDOWSX64UNICODE) {
            LOGGER.warn("The Windows x64 Unicode BusyBox failed to start; trying the x86 BusyBox.");
            closeExecutableForRetry(busyBoxFailure);
            PtyProcess compatibilityProcess = null;
            try {
                executable = BusyBoxPlatform.extract(LangPlatform.WINDOWSX86);
                compatibilityProcess = startProcess(
                    new String[] {executable.getPath().toString(), "sh"},
                    environment
                );
                if (!exitedDuringStartup(compatibilityProcess)) {
                    busyBoxX86Compatibility = true;
                    LOGGER.warn("Using the Windows x86 BusyBox compatibility binary.");
                    return compatibilityProcess;
                }
                IOException compatibilityExit = new IOException(
                    "Windows x86 BusyBox exited during startup: " + processDiagnostics(compatibilityProcess)
                );
                busyBoxFailure.addSuppressed(compatibilityExit);
                LOGGER.warn(compatibilityExit.getMessage());
            } catch (IOException | RuntimeException | LinkageError compatibilityFailure) {
                terminateFailedProcess(compatibilityProcess, compatibilityFailure);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Windows x86 BusyBox startup was interrupted.", compatibilityFailure);
                }
                busyBoxFailure.addSuppressed(compatibilityFailure);
                LOGGER.warn("Windows x86 BusyBox compatibility startup failed.", compatibilityFailure);
            }
        }

        String[] fallbackCommand = getSystemShellCommand(environment);
        LOGGER.warn(
            "BusyBox PTY startup failed on {}; trying system shell {}. Cause: {}",
            platform,
            fallbackCommand[0],
            busyBoxFailure.toString()
        );
        PtyProcess fallback = null;
        try {
            fallback = startProcess(fallbackCommand, environment);
            if (exitedDuringStartup(fallback)) {
                throw new IOException(
                    "System shell exited during startup: " + processDiagnostics(fallback)
                );
            }
            systemShellFallback = true;
            LOGGER.warn("Using system shell fallback for BusyBox terminal on {}.", platform);
            return fallback;
        } catch (IOException | RuntimeException | LinkageError fallbackFailure) {
            terminateFailedProcess(fallback, fallbackFailure);
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("System shell startup was interrupted.", fallbackFailure);
            }
            throw new FallbackStartupException(
                "BusyBox and the system shell fallback both failed to start. BusyBox: "
                    + describeFailure(busyBoxFailure) + "; system fallback: " + fallbackFailure,
                busyBoxFailure,
                fallbackFailure
            );
        }
    }

    private String describeFailure(Throwable failure) {
        StringBuilder description = new StringBuilder(failure.toString());
        for (Throwable additionalFailure : failure.getSuppressed()) {
            description.append("; additional failure: ").append(additionalFailure);
        }
        return description.toString();
    }

    private String[] getSystemShellCommand(Map<String, String> environment) {
        if ("windows".equals(platform.getOs())) {
            String commandProcessor = environment.entrySet().stream()
                .filter(entry -> "ComSpec".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
            return new String[] {
                commandProcessor == null || commandProcessor.isBlank() ? "cmd.exe" : commandProcessor
            };
        }
        return new String[] {"/bin/sh", "-i"};
    }

    private void closeExecutableForRetry(Throwable failure) {
        BusyBoxExecutable currentExecutable = executable;
        executable = null;
        if (currentExecutable == null) {
            return;
        }
        try {
            currentExecutable.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
            LOGGER.warn("Unable to remove the failed Windows x64 BusyBox executable.", exception);
        }
    }

    private String getConnectorName() {
        if (systemShellFallback) {
            return "System shell (BusyBox fallback)";
        }
        if (busyBoxX86Compatibility) {
            return "BusyBox x86 compatibility build";
        }
        return "BusyBox";
    }

    private void terminateFailedProcess(PtyProcess failedProcess, Throwable failure) {
        if (failedProcess == null) {
            return;
        }
        if (!isProcessAlive(failedProcess)) {
            return;
        }

        boolean interrupted = Thread.interrupted();
        try {
            ProcessTreeTerminator.terminate(failedProcess, platform);
            if (!failedProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(failedProcess)) {
                ProcessTreeTerminator.terminate(failedProcess, platform);
                if (isProcessAlive(failedProcess)) {
                    IOException cleanupFailure = new IOException(
                        "Failed PTY process " + failedProcess.pid() + " remained alive after termination."
                    );
                    failure.addSuppressed(cleanupFailure);
                    LOGGER.error(cleanupFailure.getMessage());
                }
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            try {
                ProcessTreeTerminator.terminate(failedProcess, platform);
                if (isProcessAlive(failedProcess)) {
                    failure.addSuppressed(new IOException(
                        "Failed PTY process " + failedProcess.pid() + " remained alive after interrupted cleanup."
                    ));
                }
            } catch (RuntimeException terminationFailure) {
                failure.addSuppressed(terminationFailure);
            }
            failure.addSuppressed(exception);
        } catch (RuntimeException exception) {
            failure.addSuppressed(exception);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean exitedDuringStartup(PtyProcess process) throws IOException {
        try {
            process.onExit().get(STARTUP_PROBE_MILLIS, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking whether the PTY process started.", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Unable to observe the PTY process startup.", exception.getCause());
        }
    }

    private String processDiagnostics(PtyProcess activeProcess) {
        boolean ptyAlive = isProcessAlive(activeProcess);
        String details = "platform=" + platform
            + ", os=" + System.getProperty("os.name")
            + ", arch=" + System.getProperty("os.arch")
            + ", java=" + System.getProperty("java.version")
            + ", pid=" + activeProcess.pid()
            + ", ptyAlive=" + ptyAlive
            + ", processHandleAlive=" + isProcessHandleAlive(activeProcess.pid());
        if (platform == LangPlatform.MACOSAARCH64) {
            details += ", osVersion=" + System.getProperty("os.version");
        }
        if (ptyAlive) {
            return details;
        }
        try {
            return details + ", exitCode=" + activeProcess.exitValue();
        } catch (IllegalThreadStateException exception) {
            return details + ", exitCode=unavailable";
        }
    }

    private void cleanupFailedStart(Throwable failure) {
        PtyProcess activeProcess = process;
        if (activeProcess != null && isProcessAlive(activeProcess)) {
            try {
                ProcessTreeTerminator.terminate(activeProcess, platform);
            } catch (RuntimeException exception) {
                failure.addSuppressed(exception);
            }
        }
        ACTIVE_SESSIONS.remove(this);

        BusyBoxExecutable extractedExecutable = executable;
        executable = null;
        if (extractedExecutable != null) {
            try {
                extractedExecutable.close();
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            }
        }
    }

    private static final class BusyBoxTtyConnector extends ProcessTtyConnector {

        private final PtyProcess process;
        private final String name;

        private BusyBoxTtyConnector(PtyProcess process, Charset charset, String name) {
            super(process, charset);
            this.process = process;
            this.name = name;
        }

        @Override
        public void resize(TermSize termSize) {
            if (isConnected()) {
                process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
            }
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
