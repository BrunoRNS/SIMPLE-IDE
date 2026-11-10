package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    BusyBoxTerminalSession(LangPlatform platform) {
        this.platform = platform;
    }

    void start() throws IOException {
        executable = BusyBoxPlatform.extract(platform);
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        process = new PtyProcessBuilder()
            .setCommand(new String[] {executable.getPath().toString(), "sh"})
            .setEnvironment(environment)
            .setInitialColumns(TERMINAL_COLUMNS)
            .setInitialRows(TERMINAL_ROWS)
            .setSpawnProcessUsingJdkOnMacIntel(platform != LangPlatform.MACOSINTEL)
            .start();
        ACTIVE_SESSIONS.add(this);
        if (SHUTDOWN_STARTED.get()) {
            close(true);
            return;
        }
        connector = new BusyBoxTtyConnector(process, StandardCharsets.UTF_8);
    }

    boolean isAlive() {
        PtyProcess activeProcess = process;
        return activeProcess != null && activeProcess.isAlive();
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

    void forceTerminate() {
        terminationRequested.set(true);
        PtyProcess activeProcess = process;
        if (activeProcess == null || !activeProcess.isAlive()) {
            return;
        }
        try {
            ProcessTreeTerminator.terminate(activeProcess);
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to force the BusyBox process to exit.", exception);
        }
    }

    void forceTerminateAndWait() {
        forceTerminate();
        PtyProcess activeProcess = process;
        if (activeProcess == null || !activeProcess.isAlive()) {
            return;
        }

        boolean interrupted = Thread.interrupted();
        try {
            if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && activeProcess.isAlive()) {
                ProcessTreeTerminator.terminate(activeProcess);
                if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && activeProcess.isAlive()) {
                    LOGGER.error(
                        "BusyBox process {} is still alive after forced termination.",
                        activeProcess.pid()
                    );
                }
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
            if (activeProcess != null && activeProcess.isAlive()) {
                if (force) {
                    forceTerminate();
                } else {
                    activeProcess.destroy();
                }
                if (activeProcess.isAlive() && !activeProcess.waitFor(2, TimeUnit.SECONDS)) {
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

    private static final class BusyBoxTtyConnector extends ProcessTtyConnector {

        private final PtyProcess process;

        private BusyBoxTtyConnector(PtyProcess process, Charset charset) {
            super(process, charset);
            this.process = process;
        }

        @Override
        public void resize(TermSize termSize) {
            if (isConnected()) {
                process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
            }
        }

        @Override
        public String getName() {
            return "BusyBox";
        }
    }
}
