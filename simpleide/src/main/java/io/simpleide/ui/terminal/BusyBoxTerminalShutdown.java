package io.simpleide.ui.terminal;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.pty4j.PtyProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.ui.terminal.core.busybox.BusyBoxExecutable;

/**
 * Stops a BusyBox terminal session and releases its process resources.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class BusyBoxTerminalShutdown {

	private static final Logger LOGGER = LoggerFactory.getLogger(BusyBoxTerminalShutdown.class);

	private static final long GRACEFUL_CLOSE_TIMEOUT_SECONDS = 2;

	private final BusyBoxTerminalSession session;

	private final LangPlatform platform;

	BusyBoxTerminalShutdown(BusyBoxTerminalSession session, LangPlatform platform) {
		this.session = session;
		this.platform = platform;
	}

	void forceTerminate() {
		session.terminationRequested.set(true);
		PtyProcess activeProcess = session.state.get().process();
		if (activeProcess == null || !isProcessAlive(activeProcess)) {
			LOGGER.debug("Skipping forced terminal shutdown: {}", session.processDiagnostics());
			return;
		}
		LOGGER.info("Forcing terminal process-tree shutdown: {}", session.processDiagnostics());
		boolean interrupted = Thread.interrupted();
		try {
			ProcessTreeTerminator.terminate(activeProcess, platform);
			if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(activeProcess)) {
				LOGGER.warn("Terminal shell remained alive after forced termination: {}", session.processDiagnostics());
			}
			LOGGER.info("Forced terminal shutdown returned: {}", session.processDiagnostics());
		}
		catch (InterruptedException exception) {
			interrupted = true;
			LOGGER.warn("Interrupted while forcing the BusyBox process to exit: {}", session.processDiagnostics(),
					exception);
		}
		catch (RuntimeException exception) {
			LOGGER.error("Unable to force the BusyBox process to exit: {}", session.processDiagnostics(), exception);
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Force-terminates the shell process and waits briefly for it to exit.
	 *
	 * <p>
	 * The method never throws for a process that is already gone; termination failures
	 * are logged. It is used when graceful shutdown does not finish in time.
	 */
	void forceTerminateAndWait() {
		forceTerminate();
		PtyProcess activeProcess = session.state.get().process();
		if (activeProcess == null || !isProcessAlive(activeProcess)) {
			return;
		}

		boolean interrupted = Thread.interrupted();
		try {
			if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(activeProcess)) {
				ProcessTreeTerminator.terminate(activeProcess, platform);
				if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(activeProcess)) {
					LOGGER.error("BusyBox process {} is still alive after forced termination.", activeProcess.pid());
				}
			}
		}
		catch (InterruptedException exception) {
			interrupted = true;
			LOGGER.warn("Interrupted while closing terminal; falling back to forced termination: {}.",
					session.processDiagnostics(), exception);
			forceTerminate();
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	void closeGracefullyAndWait() {
		session.terminationRequested.set(true);
		BusyBoxSessionSnapshot snapshot = session.state.get();
		PtyProcess activeProcess = snapshot.process();
		if (activeProcess == null) {
			LOGGER.info("Skipping graceful terminal shutdown because no process was started: {}.",
					session.processDiagnostics());
			return;
		}
		if (!isProcessAlive(activeProcess)) {
			LOGGER.info("Skipping graceful terminal shutdown because the process already exited: {}.",
					session.processDiagnostics());
			return;
		}

		List<ProcessHandle> descendants = ProcessTreeTerminator.captureDescendants(activeProcess);
		LOGGER.info("Attempting graceful terminal shutdown: {}; captured descendants={}", session.processDiagnostics(),
				processTreeDiagnostics(descendants));
		if (platform == LangPlatform.MACOSAARCH64) {
			LOGGER.info("Stopping captured macOS ARM64 child processes before sending EOF: session={}, descendants={}",
					activeProcess.pid(), processTreeDiagnostics(descendants));
			ProcessTreeTerminator.terminateMacArm64Descendants(activeProcess, descendants);
		}
		boolean interrupted = Thread.interrupted();
		try {
			BusyBoxTtyConnector activeConnector = snapshot.connector();
			if (activeConnector != null) {
				try {
					LOGGER.info("Sending {} to terminal session {} through connector {}.",
							"windows".equals(platform.getOs()) && snapshot.systemShellFallback() ? "Ctrl-Z" : "EOF",
							activeProcess.pid(), activeConnector.getName());
					activeConnector.write("windows".equals(platform.getOs()) && snapshot.systemShellFallback()
							? "\u001A\r\n" : "\u0004");
				}
				catch (IOException exception) {
					LOGGER.warn("Unable to send EOF to terminal session {}.", activeProcess.pid(), exception);
				}
			}

			if (activeProcess.waitFor(GRACEFUL_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				ProcessTreeTerminator.terminateCapturedDescendants(descendants, platform);
				awaitCapturedDescendants(descendants, activeProcess.pid());
				LOGGER.info("Terminal session exited after EOF: {}; remaining captured descendants={}",
						session.processDiagnostics(), processTreeDiagnostics(descendants));
				return;
			}

			LOGGER.info(
					"Terminal session did not exit within {} seconds after EOF; terminating its process tree: {}; "
							+ "remaining descendants={}",
					GRACEFUL_CLOSE_TIMEOUT_SECONDS, session.processDiagnostics(), processTreeDiagnostics(descendants));
			ProcessTreeTerminator.terminate(activeProcess, platform);
			if (!activeProcess.waitFor(2, TimeUnit.SECONDS) && isProcessAlive(activeProcess)) {
				LOGGER.error("Terminal session remained alive after process-tree termination: {}; descendants={}",
						session.processDiagnostics(), processTreeDiagnostics(descendants));
			}
			else {
				LOGGER.info("Terminal session exited after process-tree termination: {}; descendants={}",
						session.processDiagnostics(), processTreeDiagnostics(descendants));
			}
		}
		catch (InterruptedException exception) {
			interrupted = true;
			forceTerminate();
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Closes the session, stopping the shell and releasing the extracted executable.
	 * Calling the method more than once is a no-op.
	 */
	void close() {
		// Never repeat a potentially slow graceful shutdown when the caller already
		// terminated the session (forceTerminate, closeGracefullyAndWait or a failed
		// start).
		if (!session.terminationRequested.get()) {
			closeGracefullyAndWait();
		}
		close(false);
	}

	void close(boolean force) {
		boolean alreadyTerminating = session.terminationRequested.getAndSet(true);
		if (!session.closed.compareAndSet(false, true)) {
			return;
		}

		PtyProcess activeProcess = session.state.get().process();
		boolean interrupted = Thread.interrupted();
		try {
			if (activeProcess != null && isProcessAlive(activeProcess)) {
				if (force || alreadyTerminating) {
					forceTerminate();
				}
				else {
					activeProcess.destroy();
					if (isProcessAlive(activeProcess) && !activeProcess.waitFor(2, TimeUnit.SECONDS)) {
						forceTerminate();
					}
				}
			}
		}
		catch (InterruptedException exception) {
			interrupted = true;
			forceTerminate();
		}
		catch (RuntimeException exception) {
			LOGGER.error("Unable to stop the BusyBox process during cleanup.", exception);
			forceTerminate();
		}
		finally {
			closeResources();
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	void detach() {
		session.terminationRequested.set(true);
		if (!session.closed.compareAndSet(false, true)) {
			return;
		}
		closeResources();
	}

	private void closeResources() {
		try {
			BusyBoxTtyConnector activeConnector = session.state.get().connector();
			if (activeConnector != null) {
				activeConnector.close();
			}
		}
		catch (RuntimeException exception) {
			LOGGER.error("Unable to close the BusyBox PTY connector.", exception);
		}
		try {
			BusyBoxExecutable extractedExecutable = session.state.get().executable();
			if (extractedExecutable != null) {
				extractedExecutable.close();
			}
		}
		catch (IOException exception) {
			LOGGER.error("Unable to remove the extracted BusyBox executable.", exception);
		}
		finally {
			BusyBoxTerminalSession.ACTIVE_SESSIONS.remove(session);
		}
	}

	boolean isProcessAlive(PtyProcess activeProcess) {
		return activeProcess.isAlive();
	}

	boolean isProcessHandleAlive(long processId) {
		try {
			Optional<ProcessHandle> handle = ProcessHandle.of(processId);
			return handle.map(ProcessHandle::isAlive).orElse(false);
		}
		catch (RuntimeException exception) {
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
							sessionPid, processTreeDiagnostics(List.of(descendant)));
					return;
				}
				try {
					TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(20)));
				}
				catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	private static String processTreeDiagnostics(List<ProcessHandle> processes) {
		return processes.stream().map(process -> {
			ProcessHandle.Info info = process.info();
			return "pid=" + process.pid() + ", alive=" + process.isAlive() + ", parentPid="
					+ process.parent().map(ProcessHandle::pid).orElse(-1L) + ", command="
					+ info.command().orElse("unknown") + ", arguments="
					+ String.join(" ", info.arguments().orElse(new String[0]));
		}).toList().toString();
	}

}
