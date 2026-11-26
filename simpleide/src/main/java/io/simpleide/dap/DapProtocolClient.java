package io.simpleide.dap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;

/**
 * Handles Debug Adapter Protocol callbacks for a session.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
/**
 * Client endpoint that translates adapter events into shared output and listener calls.
 */
final class DapProtocolClient implements IDebugProtocolClient {

	private final DapSession session;

	DapProtocolClient(DapSession session) {
		this.session = session;
	}

	@Override
	public void initialized() {
		if (session.parent == null) {
			Output.getInstance().publish(OutputType.OUTPUT, Output.INITIALIZED);
		}
		session.initialized.complete(null);
	}

	@Override
	public void output(OutputEventArguments arguments) {
		if (arguments == null || arguments.getOutput() == null) {
			return;
		}
		OutputType type = "stderr"
			.equalsIgnoreCase(arguments.getCategory())
					? OutputType.ERROR
					: "telemetry".equalsIgnoreCase(arguments.getCategory())
							|| "console".equalsIgnoreCase(arguments.getCategory()) ? OutputType.DEBUG
									: OutputType.OUTPUT;
		for (String line : arguments.getOutput().split("\\R", -1)) {
			if (!line.isEmpty()) {
				Output.getInstance().publish(type, line);
			}
		}
	}

	@Override
	public void stopped(StoppedEventArguments arguments) {
		session.sessionState.updateAndGet(state -> state.withStopped(true));
		if (session.parent != null) {
			// Execution controls follow the session that actually stopped, which for
			// adapters that launch the debuggee as a child session is the child.
			session.parent.controlTarget.set(session);
		}
		String reason = arguments == null || arguments.getReason() == null ? "unknown" : arguments.getReason();
		if (arguments != null && arguments.getThreadId() != null && arguments.getThreadId() >= 0) {
			int stoppedThread = arguments.getThreadId();
			session.sessionState.updateAndGet(state -> state.withThreadId(stoppedThread));
		}
		Output.getInstance().publish(OutputType.DEBUG, "Stopped: " + reason);
		int stoppedThread = session.sessionState.get().threadId();
		session.listener.onStopped(stoppedThread < 0 ? 0 : stoppedThread, reason);
	}

	@Override
	public void continued(org.eclipse.lsp4j.debug.ContinuedEventArguments arguments) {
		session.sessionState.updateAndGet(state -> state.withStopped(false));
		if (session.parent != null) {
			session.parent.controlTarget.compareAndSet(session, null);
		}
		session.listener.onContinued();
	}

	@Override
	public void terminated(TerminatedEventArguments arguments) {
		session.sessionState.updateAndGet(state -> state.withStopped(false));
		if (session.parent != null) {
			session.parent.controlTarget.compareAndSet(session, null);
		}
		Output.getInstance().publish(OutputType.DEBUG, "Debug session terminated.");
		session.notifyTerminated();
		session.publishFinished();
	}

	/**
	 * Starts the debuggee requested by the adapter with its streams connected to the I/O
	 * tab.
	 * @param arguments debuggee command and working directory
	 * @return the launched process information
	 */
	@Override
	public CompletableFuture<Void> startDebugging(StartDebuggingRequestArguments arguments) {
		CompletableFuture<Void> started = new CompletableFuture<>();
		DapSession.LIFECYCLE_POOL.execute(() -> {
			try {
				session.startChildSession(arguments);
				started.complete(null);
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				started.completeExceptionally(error);
			}
			catch (IOException | RuntimeException error) {
				started.completeExceptionally(error);
			}
		});
		return started;
	}

	@Override
	public CompletableFuture<RunInTerminalResponse> runInTerminal(RunInTerminalRequestArguments arguments) {
		try {
			return CompletableFuture.completedFuture(session.startDebuggee(arguments));
		}
		catch (IOException | RuntimeException error) {
			return CompletableFuture.failedFuture(error);
		}
	}

}
