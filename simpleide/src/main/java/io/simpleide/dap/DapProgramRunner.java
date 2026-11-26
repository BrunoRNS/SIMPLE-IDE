package io.simpleide.dap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;

/**
 * Launches the debugged program and forwards its standard streams.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class DapProgramRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(DapProgramRunner.class);

	private static final ManagedThreadPool PROCESS_IO_POOL = ThreadMaster.getInstance()
		.createThreadPool("dap-process-io", 16);

	private final DapSession session;

	DapProgramRunner(DapSession session) {
		this.session = session;
	}

	RunInTerminalResponse start(RunInTerminalRequestArguments arguments) throws IOException {
		if (arguments == null || arguments.getArgs() == null || arguments.getArgs().length == 0) {
			throw new IOException("The debug adapter did not provide a program command.");
		}
		ProcessBuilder builder = new ProcessBuilder(Arrays.asList(arguments.getArgs()));
		if (arguments.getCwd() != null && !arguments.getCwd().isBlank()) {
			builder.directory(Path.of(arguments.getCwd()).toFile());
		}
		if (arguments.getEnv() != null) {
			builder.environment().putAll(arguments.getEnv());
		}
		Process launched = builder.start();
		synchronized (session.stateLock) {
			if (session.sessionState.get().closed()) {
				launched.destroyForcibly();
				throw new IOException("Debug session closed before its program started.");
			}
			if (session.debuggee != null && session.debuggee.isAlive()) {
				launched.destroyForcibly();
				throw new IOException("A debugged program is already running.");
			}
			session.debuggee = launched;
		}
		drainDebuggeeStream(launched.getInputStream(), OutputType.OUTPUT);
		drainDebuggeeStream(launched.getErrorStream(), OutputType.ERROR);
		startDebuggeeInputForwarder(launched);

		RunInTerminalResponse response = new RunInTerminalResponse();
		long processId = launched.pid();
		if (processId <= Integer.MAX_VALUE) {
			response.setProcessId((int) processId);
		}
		return response;
	}

	void drainAdapterErrors(Process started) {
		Runnable drain = () -> {
			try (InputStream errors = started.getErrorStream()) {
				byte[] buffer = new byte[4096];
				int read;
				while ((read = errors.read(buffer)) != -1) {
					String text = new String(buffer, 0, read, StandardCharsets.UTF_8).stripTrailing();
					if (!text.isBlank()) {
						LOGGER.debug("[DAP] {}", text);
						Output.getInstance().publish(OutputType.DEBUG, text);
					}
				}
			}
			catch (IOException error) {
				if (!session.sessionState.get().closed()) {
					LOGGER.debug("[DAP] Unable to read adapter diagnostics.", error);
				}
			}
		};
		PROCESS_IO_POOL.execute(drain);
	}

	private void drainDebuggeeStream(InputStream stream, OutputType type) {
		PROCESS_IO_POOL.execute(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					Output.getInstance().publish(type, line);
				}
			}
			catch (IOException error) {
				if (!session.sessionState.get().closed()) {
					LOGGER.debug("Unable to read debugged program output.", error);
				}
			}
		});
	}

	private void startDebuggeeInputForwarder(Process launched) {
		PROCESS_IO_POOL.execute(() -> {
			try (BufferedWriter writer = new BufferedWriter(
					new OutputStreamWriter(launched.getOutputStream(), StandardCharsets.UTF_8))) {
				publishWaitingForStdin();
				while (!session.sessionState.get().closed() && launched.isAlive()) {
					String line = session.input.get(1);
					if (line != null) {
						writer.write(line);
						writer.newLine();
						writer.flush();
						session.waitingForStdinPublished.set(false);
						publishWaitingForStdin();
					}
				}
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
			}
			catch (IOException error) {
				if (!session.sessionState.get().closed() && launched.isAlive()) {
					LOGGER.debug("Unable to forward input to the debugged program.", error);
					Output.getInstance().publish(OutputType.ERROR, "Unable to forward input to the debugged program.");
				}
			}
		});
	}

	private void publishWaitingForStdin() {
		if (session.waitingForStdinPublished.compareAndSet(false, true)) {
			Output.getInstance().publish(OutputType.OUTPUT, Output.WAITING_FOR_STDIN);
		}
	}

}
