package io.simpleide.ui.terminal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.pty4j.unix.CLibrary;
import com.pty4j.unix.Pty;
import com.pty4j.unix.UnixPtyException;
import com.pty4j.util.PtyUtil;

final class MacArm64PtyProcess extends PtyProcess {

    private static final Logger LOGGER = LoggerFactory.getLogger(MacArm64PtyProcess.class);
    private static final long WINDOW_SIZE_TIMEOUT_SECONDS = 2;
    private static final long WINDOW_SIZE_RETRY_DELAY_MILLIS = 2;

    private final Pty pty;
    private final AtomicBoolean ptyClosed = new AtomicBoolean();
    private Process process;

    private MacArm64PtyProcess(Pty pty) {
        this.pty = pty;
    }

    static MacArm64PtyProcess start(
        String[] command,
        Map<String, String> environment,
        int initialColumns,
        int initialRows
    ) throws IOException {
        Pty pty = new Pty();
        Process child = null;
        MacArm64PtyProcess ptyProcess = new MacArm64PtyProcess(pty);
        try {
            child = startWithPty4jSpawnHelper(command, environment, pty);
            ptyProcess.process = child;
            ptyProcess.initializeWindowSize(initialColumns, initialRows);
            return ptyProcess;
        } catch (InterruptedException exception) {
            cleanupFailedStart(pty, child, exception);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while initializing the macOS ARM64 PTY.", exception);
        } catch (IOException | RuntimeException exception) {
            cleanupFailedStart(pty, child, exception);
            throw exception;
        }
    }

    private static Process startWithPty4jSpawnHelper(
        String[] command,
        Map<String, String> environment,
        Pty pty
    ) throws IOException {
        File spawnHelper = PtyUtil.resolveNativeFile("pty4j-unix-spawn-helper");
        List<String> helperCommand = new ArrayList<>(command.length + 7);
        helperCommand.add(spawnHelper.getAbsolutePath());
        helperCommand.add(".");
        helperCommand.add("0");
        helperCommand.add(pty.getSlaveName());
        helperCommand.add(Integer.toString(pty.getMasterFD()));
        helperCommand.add("");
        helperCommand.add("-1");
        helperCommand.addAll(List.of(command));

        ProcessBuilder builder = new ProcessBuilder(helperCommand)
            .directory(new File("."))
            .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(environment);
        return builder.start();
    }

    private void initializeWindowSize(int columns, int rows) throws InterruptedException, IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WINDOW_SIZE_TIMEOUT_SECONDS);
        UnixPtyException lastException;
        do {
            try {
                pty.setWindowSize(new WinSize(columns, rows), this);
                return;
            } catch (UnixPtyException exception) {
                lastException = exception;
                long remainingNanos = deadline - System.nanoTime();
                if (exception.getErrno() != CLibrary.ENOTTY || remainingNanos <= 0) {
                    break;
                }
                long delayNanos = Math.min(
                    TimeUnit.MILLISECONDS.toNanos(WINDOW_SIZE_RETRY_DELAY_MILLIS),
                    remainingNanos
                );
                awaitRetryDelay(delayNanos);
            } catch (RuntimeException exception) {
                throw new IOException("Unable to initialize the macOS ARM64 PTY window size.", exception);
            }
        } while (System.nanoTime() < deadline);
        LOGGER.warn("Unable to set the initial macOS ARM64 PTY window size.", lastException);
    }

    private static void awaitRetryDelay(long delayNanos) throws InterruptedException {
        long retryDeadline = System.nanoTime() + delayNanos;
        long remainingNanos;
        while ((remainingNanos = retryDeadline - System.nanoTime()) > 0) {
            LockSupport.parkNanos(remainingNanos);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
    }

    private static void cleanupFailedStart(Pty pty, Process process, Throwable failure) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    failure.addSuppressed(new IOException("The macOS ARM64 PTY helper did not stop after startup failed."));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failure.addSuppressed(exception);
            }
        }
        try {
            pty.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    void closePty() throws IOException {
        if (ptyClosed.compareAndSet(false, true)) {
            pty.close();
        }
    }

    @Override
    public OutputStream getOutputStream() {
        return pty.getOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return pty.getInputStream();
    }

    @Override
    public InputStream getErrorStream() {
        return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return process.waitFor(timeout, unit);
    }

    @Override
    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void destroy() {
        process.destroy();
    }

    @Override
    public Process destroyForcibly() {
        process.destroyForcibly();
        return this;
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public long pid() {
        return process.pid();
    }

    @Override
    public CompletableFuture<Process> onExit() {
        return process.onExit().thenApply(ignored -> this);
    }

    @Override
    public void setWinSize(WinSize winSize) {
        try {
            pty.setWindowSize(winSize, this);
        } catch (UnixPtyException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public WinSize getWinSize() throws IOException {
        return pty.getWinSize(this);
    }

    @Override
    public boolean isConsoleMode() {
        return false;
    }
}
