package io.simpleide.dap;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ends a debug session and releases its adapter, program and socket resources.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class DapSessionLifecycle {

	private static final Logger LOGGER = LoggerFactory.getLogger(DapSessionLifecycle.class);

	private DapSessionLifecycle() {
	}

	static void close(DapSession session) {
		synchronized (session.stateLock) {
			if (session.sessionState.get().closed()) {
				return;
			}
			session.sessionState.updateAndGet(state -> state.withClosed(true));
		}
		for (DapSession child : session.children) {
			child.close();
		}
		session.children.clear();
		IDebugProtocolServer activeServer = session.server;
		if (activeServer != null) {
			try {
				DisconnectArguments arguments = new DisconnectArguments();
				arguments.setTerminateDebuggee(true);
				activeServer.disconnect(arguments);
			}
			catch (RuntimeException error) {
				LOGGER.debug("Unable to disconnect from the debug adapter cleanly.", error);
			}
		}
		closeQuietly(session.socket);
		terminateProcess(session.debuggee);
		session.debuggee = null;
		terminateProcess(session.process);
		session.process = null;
		session.notifyTerminated();
		if (session.sessionState.get().protocolStarted()) {
			session.publishFinished();
		}
	}

	private static void terminateProcess(Process activeProcess) {
		if (activeProcess == null || !activeProcess.isAlive()) {
			return;
		}
		activeProcess.destroy();
		DapSession.LIFECYCLE_POOL.execute(() -> {
			try {
				if (!activeProcess.waitFor(2, TimeUnit.SECONDS)) {
					activeProcess.destroyForcibly();
				}
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				activeProcess.destroyForcibly();
			}
		});
	}

	private static void closeQuietly(Socket socket) {
		if (socket == null) {
			return;
		}
		try {
			socket.close();
		}
		catch (IOException error) {
			LOGGER.debug("Unable to close the debug adapter connection cleanly.", error);
		}
	}

}
