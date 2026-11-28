package io.simpleide.dap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts a debug adapter process, connects the protocol and launches the program.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class DapSessionStarter {

	private static final Logger LOGGER = LoggerFactory.getLogger(DapSessionStarter.class);

	private static final long START_TIMEOUT_SECONDS = 30;

	/**
	 * Adapters such as delve send the {@code initialized} event only after building the
	 * program, so the wait must cover a cold Go build.
	 */
	private static final long INITIALIZED_EVENT_TIMEOUT_SECONDS = 90;

	/**
	 * Loopback addresses tried when connecting to a server-style adapter. Adapters bind
	 * either IPv4 (debugpy and delve) or IPv6 (the JavaScript adapter) loopback, so both
	 * are attempted.
	 */
	private static final List<String> LOOPBACK_HOSTS = List.of("127.0.0.1", "::1");

	private final DapSession session;

	DapSessionStarter(DapSession session) {
		this.session = session;
	}

	void start(List<Integer> breakpoints) throws IOException, InterruptedException {
		List<Integer> initialBreakpoints = breakpoints == null ? List.of() : List.copyOf(breakpoints);
		session.sessionState.updateAndGet(state -> state.withBreakpoints(initialBreakpoints));
		if (session.externalPort > 0) {
			session.sessionState.updateAndGet(state -> state.withAdapterPort(session.externalPort));
			connect(session.externalPort);
		}
		else {
			List<String> commandLine;
			int port = 0;
			if ("tcp".equals(session.adapter.getTransport())) {
				port = findFreePort();
				int adapterTcpPort = port;
				session.sessionState.updateAndGet(state -> state.withAdapterPort(adapterTcpPort));
				commandLine = session.adapter.commandLine(session.installDirectory.toString(),
						session.program.toString(), port);
				startProcess(commandLine, port);
				connect(port);
			}
			else {
				commandLine = session.adapter.commandLine(session.installDirectory.toString(),
						session.program.toString());
				startProcess(commandLine, port);
				synchronized (session.stateLock) {
					session.socket = null;
				}
			}
		}

		InputStream input;
		OutputStream output;
		synchronized (session.stateLock) {
			input = session.socket == null ? session.process.getInputStream() : session.socket.getInputStream();
			output = session.socket == null ? session.process.getOutputStream() : session.socket.getOutputStream();
		}
		Launcher<IDebugProtocolServer> launcher = DSPLauncher.createClientLauncher(session.client, input, output);
		synchronized (session.stateLock) {
			session.server = launcher.getRemoteProxy();
		}
		launcher.startListening();

		initialize();
		session.sessionState.updateAndGet(state -> state.withProtocolStarted(true));
		CompletableFuture<Void> launchRequest = launch();
		awaitInitializedEvent(launchRequest);
		await(sendBreakpoints(session.program, breakpoints));
		if (session.capabilities != null
				&& Boolean.TRUE.equals(session.capabilities.getSupportsConfigurationDoneRequest())) {
			await(session.server.configurationDone(new ConfigurationDoneArguments()));
		}
		await(launchRequest);
	}

	private void startProcess(List<String> commandLine, int port) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(commandLine);
		builder.redirectErrorStream(false);
		Map<String, String> environment = session.adapter.environment(session.installDirectory.toString(),
				session.program.toString(), port);
		if (!environment.isEmpty()) {
			builder.environment().putAll(environment);
		}
		session.process = builder.start();
		session.programRunner.drainAdapterErrors(session.process);
	}

	/**
	 * Waits briefly for the adapter's {@code initialized} event.
	 *
	 * <p>
	 * Most adapters send the event right after the {@code initialize} response, but some
	 * send it only once the launch request arrives. Since the event only delays the
	 * launch request, a bounded wait keeps those adapters working instead of blocking
	 * startup.
	 *
	 * <p>
	 * A failed launch request ends the wait immediately and is reported to the user.
	 * Adapters such as delve reject the launch before the debugger exists, so every later
	 * request (breakpoints, configurationDone) would fail with the unrelated message
	 * {@code No debug session started} instead of the real problem, for example a Go
	 * toolchain too old for the bundled adapter.
	 * @param launchRequest pending response to the launch request
	 * @throws IOException when the adapter rejects the launch request
	 * @throws InterruptedException when the calling thread is interrupted
	 */
	private void awaitInitializedEvent(CompletableFuture<Void> launchRequest) throws IOException, InterruptedException {
		CompletableFuture<Object> firstOutcome = CompletableFuture.anyOf(session.initialized, launchRequest);
		try {
			firstOutcome.get(INITIALIZED_EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw error;
		}
		catch (TimeoutException error) {
			LOGGER.debug("The debug adapter did not send the initialized event before launch.");
		}
		catch (ExecutionException error) {
			LOGGER.debug("The debug adapter failed to initialize.", error);
		}
		if (launchRequest.isCompletedExceptionally()) {
			reportLaunchFailure(launchRequest);
		}
	}

	private static void reportLaunchFailure(CompletableFuture<Void> launchRequest) throws IOException {
		Throwable failure;
		try {
			launchRequest.join();
			return;
		}
		catch (CompletionException error) {
			failure = error.getCause() == null ? error : error.getCause();
		}
		throw new IOException("The debug adapter rejected the launch request: " + failure.getMessage(), failure);
	}

	private void connect(int port) throws IOException, InterruptedException {
		IOException lastError = null;
		for (int attempt = 0; attempt < 100; attempt++) {
			if (session.sessionState.get().closed()) {
				throw new IOException("Debug session was closed before the adapter started.");
			}
			for (String host : LOOPBACK_HOSTS) {
				try {
					Socket candidate = new Socket();
					candidate.connect(new InetSocketAddress(host, port), 500);
					synchronized (session.stateLock) {
						session.socket = candidate;
					}
					return;
				}
				catch (IOException error) {
					lastError = error;
				}
			}
			Thread.sleep(100);
		}
		throw new IOException("Unable to connect to the debug adapter on port " + port, lastError);
	}

	private void initialize() throws IOException, InterruptedException {
		InitializeRequestArguments arguments = new InitializeRequestArguments();
		arguments.setClientID("simpleide");
		arguments.setClientName("Simple IDE");
		if (session.adapter != null) {
			arguments.setAdapterID(session.adapter.getId());
		}
		arguments.setLinesStartAt1(true);
		arguments.setColumnsStartAt1(true);
		arguments.setPathFormat("path");
		arguments.setSupportsRunInTerminalRequest(true);
		Capabilities result = await(session.server.initialize(arguments));
		synchronized (session.stateLock) {
			session.capabilities = result;
		}
	}

	private CompletableFuture<Void> launch() {
		Map<String, Object> arguments;
		if (session.adapter == null) {
			arguments = new LinkedHashMap<>(session.externalLaunchArguments);
		}
		else {
			arguments = new LinkedHashMap<>(session.adapter.launchArguments(session.program));
			arguments.putIfAbsent("program", session.program.toString());
			arguments.putIfAbsent("cwd",
					session.program.getParent() == null ? "." : session.program.getParent().toString());
			arguments.putIfAbsent("name", session.adapter.getName());
		}
		return session.server.launch(arguments).thenApply(ignored -> null);
	}

	private CompletableFuture<org.eclipse.lsp4j.debug.SetBreakpointsResponse> sendBreakpoints(Path file,
			List<Integer> lines) {
		return session.sendBreakpoints(file, lines);
	}

	private <T> T await(CompletableFuture<T> future) throws IOException, InterruptedException {
		try {
			return future.get(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw error;
		}
		catch (TimeoutException error) {
			throw new IOException("The debug adapter did not answer in time.", error);
		}
		catch (ExecutionException error) {
			Throwable cause = error.getCause() == null ? error : error.getCause();
			throw new IOException("The debug adapter rejected a protocol request: " + cause.getMessage(), cause);
		}
	}

	private static int findFreePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		}
	}

}
