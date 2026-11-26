package io.simpleide.dap;

import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;

import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;

/**
 * Sends execution-control requests for a debug session.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class DapExecutionControl {

	private static final Logger LOGGER = LoggerFactory.getLogger(DapExecutionControl.class);

	private final DapSession session;

	DapExecutionControl(DapSession session) {
		this.session = session;
	}

	void resume() {
		if (delegateToChild(DapSession::resume)) {
			return;
		}
		sendWhenStopped(activeThread -> {
			ContinueArguments arguments = new ContinueArguments();
			arguments.setThreadId(activeThread);
			return session.server.continue_(arguments).thenApply(ignored -> null);
		});
	}

	void pause() {
		if (delegateToChild(DapSession::pause)) {
			return;
		}
		sendUsingThread(activeThread -> {
			PauseArguments arguments = new PauseArguments();
			arguments.setThreadId(activeThread);
			return session.server.pause(arguments);
		});
	}

	void stepOver() {
		if (delegateToChild(DapSession::stepOver)) {
			return;
		}
		sendWhenStopped(activeThread -> {
			NextArguments arguments = new NextArguments();
			arguments.setThreadId(activeThread);
			return session.server.next(arguments);
		});
	}

	void stepInto() {
		if (delegateToChild(DapSession::stepInto)) {
			return;
		}
		sendWhenStopped(activeThread -> {
			StepInArguments arguments = new StepInArguments();
			arguments.setThreadId(activeThread);
			return session.server.stepIn(arguments);
		});
	}

	void stepOut() {
		if (delegateToChild(DapSession::stepOut)) {
			return;
		}
		sendWhenStopped(activeThread -> {
			StepOutArguments arguments = new StepOutArguments();
			arguments.setThreadId(activeThread);
			return session.server.stepOut(arguments);
		});
	}

	boolean supportsConfigurationDone() {
		synchronized (session.stateLock) {
			return session.capabilities != null
					&& Boolean.TRUE.equals(session.capabilities.getSupportsConfigurationDoneRequest());
		}
	}

	private boolean delegateToChild(java.util.function.Consumer<DapSession> action) {
		DapSession delegate = session.controlTarget.get();
		if (delegate != null && delegate != session) {
			action.accept(delegate);
			return true;
		}
		return false;
	}

	private void sendWhenStopped(IntFunction<CompletableFuture<Void>> request) {
		if (!session.sessionState.get().stopped()) {
			Output.getInstance().publish(OutputType.DEBUG, "Execution is running; pause before stepping.");
			return;
		}
		sendUsingThread(request);
	}

	private void sendUsingThread(IntFunction<CompletableFuture<Void>> request) {
		IDebugProtocolServer activeServer;
		synchronized (session.stateLock) {
			activeServer = session.server;
		}
		if (activeServer == null || session.sessionState.get().closed()) {
			return;
		}
		int activeThread = session.sessionState.get().threadId();
		if (activeThread >= 0) {
			sendRequest(request, activeThread);
			return;
		}
		activeServer.threads().whenComplete((response, error) -> {
			if (error != null) {
				publishControlFailure(error);
				return;
			}
			int resolved = firstThreadId(response);
			if (resolved < 0) {
				Output.getInstance().publish(OutputType.DEBUG, "No debug thread is available for this request.");
				return;
			}
			session.sessionState.updateAndGet(state -> state.withThreadId(resolved));
			sendRequest(request, resolved);
		});
	}

	private void sendRequest(IntFunction<CompletableFuture<Void>> request, int activeThread) {
		try {
			request.apply(activeThread).whenComplete((ignored, error) -> {
				if (error != null) {
					publishControlFailure(error);
				}
			});
		}
		catch (RuntimeException error) {
			publishControlFailure(error);
		}
	}

	private void publishControlFailure(Throwable error) {
		LOGGER.warn("Debug control request failed.", error);
		Output.getInstance().publish(OutputType.ERROR, "Debug control request failed: " + error.getMessage());
	}

	private static int firstThreadId(org.eclipse.lsp4j.debug.ThreadsResponse response) {
		if (response == null || response.getThreads() == null || response.getThreads().length == 0) {
			return DapSession.UNKNOWN_THREAD;
		}
		for (org.eclipse.lsp4j.debug.Thread thread : response.getThreads()) {
			if (thread != null && thread.getId() >= 0) {
				return thread.getId();
			}
		}
		return DapSession.UNKNOWN_THREAD;
	}

}
