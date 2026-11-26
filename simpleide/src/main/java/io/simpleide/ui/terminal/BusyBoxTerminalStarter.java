package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.ui.terminal.core.busybox.BusyBoxExecutable;
import io.simpleide.ui.terminal.core.busybox.BusyBoxPlatform;

/**
 * Starts the BusyBox shell for a terminal session, with system-shell fallback.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class BusyBoxTerminalStarter {

	private static final Logger LOGGER = LoggerFactory.getLogger(BusyBoxTerminalStarter.class);

	private static final int TERMINAL_COLUMNS = 80;

	private static final int TERMINAL_ROWS = 24;

	private static final long STARTUP_PROBE_MILLIS = 250;

	private final BusyBoxTerminalSession session;

	private final LangPlatform platform;

	private final Path workingDirectory;

	BusyBoxTerminalStarter(BusyBoxTerminalSession session, LangPlatform platform, Path workingDirectory) {
		this.session = session;
		this.platform = platform;
		this.workingDirectory = workingDirectory;
	}

	void start() throws IOException {
		try {
			if (Thread.currentThread().isInterrupted()) {
				throw new IOException("BusyBox startup was interrupted before the PTY process was created.");
			}

			Map<String, String> environment = new HashMap<>(System.getenv());
			environment.put("TERM", "xterm-256color");
			LangPlatform busyBoxPlatform = BusyBoxPlatform.getCompatiblePlatform(platform,
					System.getProperty("os.version"));
			if (busyBoxPlatform != platform) {
				session.state.updateAndGet(snapshot -> snapshot.withX86Compatibility(true));
				LOGGER.info("Windows version {} is below Windows 10; using the x86 BusyBox compatibility binary.",
						System.getProperty("os.version"));
			}
			String[] command = null;
			Throwable extractionFailure = null;
			try {
				BusyBoxExecutable extractedExecutable = BusyBoxPlatform.extract(busyBoxPlatform);
				session.state.updateAndGet(snapshot -> snapshot.withExecutable(extractedExecutable));
				command = busyBoxPlatform == LangPlatform.MACOSAARCH64
						? new String[] { extractedExecutable.getPath().toString(), "sh", "-i" }
						: new String[] { extractedExecutable.getPath().toString(), "sh" };
			}
			catch (IOException | RuntimeException | LinkageError exception) {
				extractionFailure = exception;
			}
			PtyProcess startedProcess = startWithFallback(command, environment, extractionFailure, busyBoxPlatform);
			session.state.updateAndGet(snapshot -> snapshot.withProcess(startedProcess));
			LOGGER.info("Terminal shell started: {}.", session.processDiagnostics());

			BusyBoxTerminalSession.ACTIVE_SESSIONS.add(session);
			if (BusyBoxTerminalSession.SHUTDOWN_STARTED.get()) {
				session.close(true);
				return;
			}
			BusyBoxTtyConnector terminalConnector = new BusyBoxTtyConnector(startedProcess, StandardCharsets.UTF_8,
					getConnectorName());
			session.state.updateAndGet(snapshot -> snapshot.withConnector(terminalConnector));
		}
		catch (IOException | RuntimeException | LinkageError exception) {
			LOGGER.error("Terminal shell startup failed: {}.", session.processDiagnostics(), exception);
			cleanupFailedStart(exception);
			throw exception;
		}
	}

	PtyProcess startWithFallback(String[] command, Map<String, String> environment) throws IOException {
		LangPlatform busyBoxPlatform = BusyBoxPlatform.getCompatiblePlatform(platform,
				System.getProperty("os.version"));
		return startWithFallback(command, environment, null, busyBoxPlatform);
	}

	private PtyProcess startWithFallback(String[] command, Map<String, String> environment,
			Throwable initialBusyBoxFailure, LangPlatform busyBoxPlatform) throws IOException {
		Throwable busyBoxFailure = initialBusyBoxFailure;
		if (busyBoxFailure == null) {
			PtyProcess candidate = null;
			try {
				candidate = startProcess(command, environment);
				if (!exitedDuringStartup(candidate)) {
					return candidate;
				}
				busyBoxFailure = new IOException(
						"BusyBox shell exited during startup: " + session.processDiagnostics(candidate));
			}
			catch (IOException | RuntimeException | LinkageError exception) {
				terminateFailedProcess(candidate, exception);
				if (Thread.currentThread().isInterrupted()) {
					throw new IOException("BusyBox startup was interrupted; the system fallback was not started.",
							exception);
				}
				busyBoxFailure = exception;
			}
		}

		if (platform == LangPlatform.WINDOWSX64UNICODE && busyBoxPlatform == LangPlatform.WINDOWSX64UNICODE) {
			LOGGER.warn("The Windows x64 Unicode BusyBox failed to start; trying the x86 BusyBox.");
			closeExecutableForRetry(busyBoxFailure);
			PtyProcess compatibilityProcess = null;
			try {
				BusyBoxExecutable extractedExecutable = BusyBoxPlatform.extract(LangPlatform.WINDOWSX86);
				session.state.updateAndGet(snapshot -> snapshot.withExecutable(extractedExecutable));
				compatibilityProcess = startProcess(new String[] { extractedExecutable.getPath().toString(), "sh" },
						environment);
				if (!exitedDuringStartup(compatibilityProcess)) {
					session.state.updateAndGet(snapshot -> snapshot.withX86Compatibility(true));
					LOGGER.warn("Using the Windows x86 BusyBox compatibility binary.");
					return compatibilityProcess;
				}
				IOException compatibilityExit = new IOException("Windows x86 BusyBox exited during startup: "
						+ session.processDiagnostics(compatibilityProcess));
				busyBoxFailure.addSuppressed(compatibilityExit);
				LOGGER.warn(compatibilityExit.getMessage());
			}
			catch (IOException | RuntimeException | LinkageError compatibilityFailure) {
				terminateFailedProcess(compatibilityProcess, compatibilityFailure);
				if (Thread.currentThread().isInterrupted()) {
					throw new IOException("Windows x86 BusyBox startup was interrupted.", compatibilityFailure);
				}
				busyBoxFailure.addSuppressed(compatibilityFailure);
				LOGGER.warn("Windows x86 BusyBox compatibility startup failed.", compatibilityFailure);
			}
		}

		String[] fallbackCommand = getSystemShellCommand(environment);
		LOGGER.warn("BusyBox PTY startup failed on {}; trying system shell {}. Cause: {}", platform, fallbackCommand[0],
				busyBoxFailure.toString());
		PtyProcess fallback = null;
		try {
			fallback = startProcess(fallbackCommand, environment);
			if (exitedDuringStartup(fallback)) {
				throw new IOException("System shell exited during startup: " + session.processDiagnostics(fallback));
			}
			session.state.updateAndGet(snapshot -> snapshot.withSystemShellFallback(true));
			LOGGER.warn("Using system shell fallback for BusyBox terminal on {}.", platform);
			return fallback;
		}
		catch (IOException | RuntimeException | LinkageError fallbackFailure) {
			terminateFailedProcess(fallback, fallbackFailure);
			if (Thread.currentThread().isInterrupted()) {
				throw new IOException("System shell startup was interrupted.", fallbackFailure);
			}
			throw new BusyBoxTerminalSession.FallbackStartupException(
					"BusyBox and the system shell fallback both failed to start. BusyBox: "
							+ describeFailure(busyBoxFailure) + "; system fallback: " + fallbackFailure,
					busyBoxFailure, fallbackFailure);
		}
	}

	private PtyProcess startProcess(String[] command, Map<String, String> environment) throws IOException {
		PtyProcessBuilder builder = new PtyProcessBuilder().setCommand(command)
			.setEnvironment(environment)
			.setInitialColumns(TERMINAL_COLUMNS)
			.setInitialRows(TERMINAL_ROWS);
		if (workingDirectory != null) {
			builder.setDirectory(workingDirectory.toString());
		}
		Pty4jCompatibility.setSpawnProcessUsingJdkOnMacIntel(builder, platform != LangPlatform.MACOSINTEL);
		return builder.setUnixOpenTtyToPreserveOutputAfterTermination(platform == LangPlatform.MACOSAARCH64).start();
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
			String commandProcessor = environment.entrySet()
				.stream()
				.filter(entry -> "ComSpec".equalsIgnoreCase(entry.getKey()))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(null);
			return new String[] {
					commandProcessor == null || commandProcessor.isBlank() ? "cmd.exe" : commandProcessor };
		}
		return new String[] { "/bin/sh", "-i" };
	}

	private void closeExecutableForRetry(Throwable failure) {
		BusyBoxExecutable currentExecutable = session.state.updateAndGet(snapshot -> snapshot.withExecutable(null))
			.executable();
		if (currentExecutable == null) {
			return;
		}
		try {
			currentExecutable.close();
		}
		catch (IOException exception) {
			failure.addSuppressed(exception);
			LOGGER.warn("Unable to remove the failed Windows x64 BusyBox executable.", exception);
		}
	}

	private String getConnectorName() {
		BusyBoxSessionSnapshot snapshot = session.state.get();
		if (snapshot.systemShellFallback()) {
			return "System shell (BusyBox fallback)";
		}
		if (snapshot.x86Compatibility()) {
			return "BusyBox x86 compatibility build";
		}
		return "BusyBox";
	}

	private void terminateFailedProcess(PtyProcess failedProcess, Throwable failure) {
		if (failedProcess == null) {
			return;
		}
		if (!session.isProcessAlive(failedProcess)) {
			return;
		}

		boolean interrupted = Thread.interrupted();
		try {
			ProcessTreeTerminator.terminate(failedProcess, platform);
			if (!failedProcess.waitFor(2, TimeUnit.SECONDS) && session.isProcessAlive(failedProcess)) {
				ProcessTreeTerminator.terminate(failedProcess, platform);
				if (session.isProcessAlive(failedProcess)) {
					IOException cleanupFailure = new IOException(
							"Failed PTY process " + failedProcess.pid() + " remained alive after termination.");
					failure.addSuppressed(cleanupFailure);
					LOGGER.error(cleanupFailure.getMessage());
				}
			}
		}
		catch (InterruptedException exception) {
			interrupted = true;
			try {
				ProcessTreeTerminator.terminate(failedProcess, platform);
				if (session.isProcessAlive(failedProcess)) {
					failure.addSuppressed(new IOException("Failed PTY process " + failedProcess.pid()
							+ " remained alive after interrupted cleanup."));
				}
			}
			catch (RuntimeException terminationFailure) {
				failure.addSuppressed(terminationFailure);
			}
			failure.addSuppressed(exception);
		}
		catch (RuntimeException exception) {
			failure.addSuppressed(exception);
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static boolean exitedDuringStartup(PtyProcess process) throws IOException {
		try {
			process.onExit().get(STARTUP_PROBE_MILLIS, TimeUnit.MILLISECONDS);
			return true;
		}
		catch (TimeoutException exception) {
			return false;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while checking whether the PTY process started.", exception);
		}
		catch (ExecutionException exception) {
			throw new IOException("Unable to observe the PTY process startup.", exception.getCause());
		}
	}

	private String processDiagnostics(PtyProcess activeProcess) {
		return session.processDiagnostics(activeProcess);
	}

	private void cleanupFailedStart(Throwable failure) {
		BusyBoxSessionSnapshot snapshot = session.state.get();
		PtyProcess activeProcess = snapshot.process();
		if (activeProcess != null && session.isProcessAlive(activeProcess)) {
			try {
				ProcessTreeTerminator.terminate(activeProcess, platform);
			}
			catch (RuntimeException exception) {
				failure.addSuppressed(exception);
			}
		}
		BusyBoxTerminalSession.ACTIVE_SESSIONS.remove(session);

		BusyBoxExecutable extractedExecutable = session.state.updateAndGet(current -> current.withExecutable(null))
			.executable();
		if (extractedExecutable != null) {
			try {
				extractedExecutable.close();
			}
			catch (IOException exception) {
				failure.addSuppressed(exception);
			}
		}
	}

}
