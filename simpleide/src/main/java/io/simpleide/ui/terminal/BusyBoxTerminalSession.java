package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.thread.ThreadMaster;

/**
 * One BusyBox shell running in a PTY process.
 *
 * <p>
 * The session extracts the embedded BusyBox binary, starts it with the proper terminal
 * environment, falls back to the system shell when the embedded binary cannot run, and
 * exposes a JediTerm connector. Closing requests a graceful exit, escalates to a force
 * termination after a timeout and deletes the extracted executable. A shutdown hook
 * force-terminates sessions that outlive the JVM.
 *
 * <p>
 * Covered by {@code BusyBoxTerminalSessionTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class BusyBoxTerminalSession implements AutoCloseable {

	static final Set<BusyBoxTerminalSession> ACTIVE_SESSIONS = ConcurrentHashMap.newKeySet();

	static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean();

	static {
		ThreadMaster.getInstance().registerShutdownAction("busybox-terminal-sessions", () -> {
			SHUTDOWN_STARTED.set(true);
			for (BusyBoxTerminalSession session : ACTIVE_SESSIONS) {
				session.forceTerminate();
				session.close(true);
			}
		});
	}

	private final LangPlatform platform;

	private final Path workingDirectory;

	final AtomicBoolean closed = new AtomicBoolean();

	final AtomicBoolean terminationRequested = new AtomicBoolean();

	final AtomicReference<BusyBoxSessionSnapshot> state = new AtomicReference<>(
			new BusyBoxSessionSnapshot(null, null, null, false, false));

	private final BusyBoxTerminalStarter starter;

	private final BusyBoxTerminalShutdown shutdown;

	BusyBoxTerminalSession(LangPlatform platform) {
		this(platform, null);
	}

	BusyBoxTerminalSession(LangPlatform platform, Path workingDirectory) {
		this.platform = platform;
		this.workingDirectory = workingDirectory == null ? null : workingDirectory.toAbsolutePath().normalize();
		this.starter = new BusyBoxTerminalStarter(this, platform, this.workingDirectory);
		this.shutdown = new BusyBoxTerminalShutdown(this, platform);
	}

	void start() throws IOException {
		starter.start();
	}

	boolean isAlive() {
		PtyProcess activeProcess = state.get().process();
		return activeProcess != null && isProcessAlive(activeProcess);
	}

	long processId() {
		PtyProcess activeProcess = state.get().process();
		if (activeProcess == null) {
			throw new IllegalStateException("BusyBox has not been started.");
		}
		return activeProcess.pid();
	}

	TtyConnector getConnector() {
		return state.get().connector();
	}

	CompletableFuture<Process> onExit() {
		PtyProcess activeProcess = state.get().process();
		if (activeProcess == null) {
			throw new IllegalStateException("BusyBox has not been started.");
		}
		return activeProcess.onExit();
	}

	boolean isTerminationRequested() {
		return terminationRequested.get();
	}

	String processDiagnostics() {
		BusyBoxSessionSnapshot snapshot = state.get();
		PtyProcess activeProcess = snapshot.process();
		String details = "platform=" + platform + ", os=" + System.getProperty("os.name") + ", arch="
				+ System.getProperty("os.arch") + ", java=" + System.getProperty("java.version")
				+ ", busyBoxX86Compatibility=" + snapshot.x86Compatibility() + ", systemShellFallback="
				+ snapshot.systemShellFallback();
		if (platform == LangPlatform.MACOSAARCH64) {
			details += ", osVersion=" + System.getProperty("os.version");
		}
		if (activeProcess == null) {
			return details + ", process=not-started";
		}

		boolean ptyAlive = isProcessAlive(activeProcess);
		String state = details + ", pid=" + activeProcess.pid() + ", ptyAlive=" + ptyAlive + ", processHandleAlive="
				+ isProcessHandleAlive(activeProcess.pid());
		if (!ptyAlive) {
			try {
				state += ", exitCode=" + activeProcess.exitValue();
			}
			catch (IllegalThreadStateException exception) {
				state += ", exitCode=unavailable";
			}
		}
		return state;
	}

	String watchDiagnostics() {
		PtyProcess activeProcess = state.get().process();
		if (activeProcess == null) {
			return processDiagnostics() + ", descendants=[]";
		}
		return processDiagnostics() + ", descendants="
				+ processTreeDiagnostics(ProcessTreeTerminator.captureDescendants(activeProcess));
	}

	static final class FallbackStartupException extends IOException {

		FallbackStartupException(String message, Throwable busyBoxFailure, Throwable fallbackFailure) {
			super(message, fallbackFailure);
			addSuppressed(busyBoxFailure);
		}

	}

	void forceTerminate() {
		shutdown.forceTerminate();
	}

	/**
	 * Force-terminates the shell process and waits briefly for it to exit.
	 *
	 * <p>
	 * The method never throws for a process that is already gone; termination failures
	 * are logged. It is used when graceful shutdown does not finish in time.
	 */
	public void forceTerminateAndWait() {
		shutdown.forceTerminateAndWait();
	}

	void closeGracefullyAndWait() {
		shutdown.closeGracefullyAndWait();
	}

	@Override
	/**
	 * Closes the session, stopping the shell and releasing the extracted executable.
	 * Calling the method more than once is a no-op.
	 */
	public void close() {
		shutdown.close();
	}

	void close(boolean force) {
		shutdown.close(force);
	}

	void detach() {
		shutdown.detach();
	}

	boolean isProcessAlive(PtyProcess activeProcess) {
		return shutdown.isProcessAlive(activeProcess);
	}

	boolean isProcessHandleAlive(long processId) {
		return shutdown.isProcessHandleAlive(processId);
	}

	PtyProcess startWithFallback(String[] command, Map<String, String> environment) throws IOException {
		return starter.startWithFallback(command, environment);
	}

	String processDiagnostics(PtyProcess activeProcess) {
		boolean ptyAlive = isProcessAlive(activeProcess);
		String details = "platform=" + platform + ", os=" + System.getProperty("os.name") + ", arch="
				+ System.getProperty("os.arch") + ", java=" + System.getProperty("java.version") + ", pid="
				+ activeProcess.pid() + ", ptyAlive=" + ptyAlive + ", processHandleAlive="
				+ isProcessHandleAlive(activeProcess.pid());
		if (platform == LangPlatform.MACOSAARCH64) {
			details += ", osVersion=" + System.getProperty("os.version");
		}
		if (ptyAlive) {
			return details;
		}
		try {
			return details + ", exitCode=" + activeProcess.exitValue();
		}
		catch (IllegalThreadStateException exception) {
			return details + ", exitCode=unavailable";
		}
	}

	private static String processTreeDiagnostics(java.util.List<ProcessHandle> processes) {
		return processes.stream().map(process -> {
			ProcessHandle.Info info = process.info();
			return "pid=" + process.pid() + ", alive=" + process.isAlive() + ", parentPid="
					+ process.parent().map(ProcessHandle::pid).orElse(-1L) + ", command="
					+ info.command().orElse("unknown") + ", arguments="
					+ String.join(" ", info.arguments().orElse(new String[0]));
		}).toList().toString();
	}

}
