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

/**
 * One running Debug Adapter Protocol session.
 *
 * <p>
 * The session launches the adapter process described by {@link DapAdapter}, connects to
 * it over standard input/output or TCP, and always starts the program with a
 * {@code launch} request; the {@code attach} request is never used. Output events are
 * forwarded to the shared {@link Output} API so the I/O tab can colour program output,
 * errors and IDE diagnostics.
 *
 * <p>
 * Execution control requests are safe to call from the Swing event dispatch thread; the
 * protocol requests are sent asynchronously by the LSP4J launcher.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class DapSession implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(DapSession.class);

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

	/**
	 * Sentinel for a thread that the adapter did not report yet. Thread {@code 0} is a
	 * valid identifier for some adapters, so it cannot double as "unknown".
	 */
	private static final int UNKNOWN_THREAD = -1;

	/**
	 * Receives execution state changes from the adapter.
	 *
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public interface Listener {

		/**
		 * Called when the adapter reports that execution stopped.
		 * @param threadId thread that stopped, or {@code 0} when the adapter did not
		 * report a thread
		 * @param reason stop reason reported by the adapter
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		default void onStopped(int threadId, String reason) {
		}

		/**
		 * Called when execution resumes.
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		default void onContinued() {
		}

		/**
		 * Called when the debug session ends.
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		default void onTerminated() {
		}

	}

	private final DapAdapter adapter;

	private final Path installDirectory;

	private final Path program;

	private final Listener listener;

	private final int externalPort;

	private final Map<String, Object> externalLaunchArguments;

	private final Client client = new Client();

	private final Object stateLock = new Object();

	private final DapSession parent;

	private final List<DapSession> children = new CopyOnWriteArrayList<>();

	private final AtomicReference<DapSession> controlTarget = new AtomicReference<>();

	private volatile int adapterPort;

	private volatile List<Integer> currentBreakpoints = List.of();

	private Process process;

	private Process debuggee;

	private Socket socket;

	private IDebugProtocolServer server;

	private Capabilities capabilities;

	private volatile int threadId = UNKNOWN_THREAD;

	private volatile boolean stopped;

	private volatile boolean closed;

	private final AtomicBoolean terminationNotified = new AtomicBoolean();

	private final AtomicBoolean finishedPublished = new AtomicBoolean();

	private final AtomicBoolean waitingForStdinPublished = new AtomicBoolean();

	private volatile boolean protocolStarted;

	private CompletableFuture<Void> initialized = new CompletableFuture<>();

	/**
	 * Creates a session that has not started yet.
	 * @param adapter adapter definition
	 * @param installDirectory directory where the adapter is installed
	 * @param program program to debug
	 * @param listener listener for execution state changes, may be {@code null}
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapSession(DapAdapter adapter, Path installDirectory, Path program, Listener listener) {
		this(adapter, installDirectory, program, listener, null);
	}

	private DapSession(DapAdapter adapter, Path installDirectory, Path program, Listener listener, DapSession parent) {
		this.adapter = adapter;
		this.installDirectory = installDirectory;
		this.program = program;
		this.listener = listener == null ? new Listener() {
		} : listener;
		this.externalPort = 0;
		this.externalLaunchArguments = Map.of();
		this.parent = parent;
	}

	/**
	 * Creates a session that connects to a debug adapter already listening on a port.
	 *
	 * <p>
	 * This is used for adapters hosted by another process, such as the Java debug plugin
	 * hosted by the JDT language server. The program starts with the supplied launch
	 * arguments instead of a local adapter process.
	 * @param program program being debugged
	 * @param port TCP port where the adapter is listening
	 * @param launchArguments properties sent in the DAP launch request
	 * @param listener listener for execution state changes, may be {@code null}
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapSession(Path program, int port, Map<String, Object> launchArguments, Listener listener) {
		this(program, port, launchArguments, listener, null);
	}

	private DapSession(Path program, int port, Map<String, Object> launchArguments, Listener listener,
			DapSession parent) {
		this.adapter = null;
		this.installDirectory = null;
		this.program = program;
		this.listener = listener == null ? new Listener() {
		} : listener;
		this.externalPort = port;
		this.externalLaunchArguments = launchArguments == null ? Map.of() : Map.copyOf(launchArguments);
		this.parent = parent;
	}

	/**
	 * Starts the adapter, initializes the protocol and launches the program.
	 * @param breakpoints initial line breakpoints of the program
	 * @throws IOException when the adapter process cannot be started or reached
	 * @throws InterruptedException when the calling thread is interrupted
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void start(List<Integer> breakpoints) throws IOException, InterruptedException {
		currentBreakpoints = breakpoints == null ? List.of() : List.copyOf(breakpoints);
		if (externalPort > 0) {
			adapterPort = externalPort;
			connect(externalPort);
		}
		else {
			List<String> commandLine;
			int port = 0;
			if ("tcp".equals(adapter.getTransport())) {
				port = findFreePort();
				adapterPort = port;
				commandLine = adapter.commandLine(installDirectory.toString(), program.toString(), port);
				startProcess(commandLine, port);
				connect(port);
			}
			else {
				commandLine = adapter.commandLine(installDirectory.toString(), program.toString());
				startProcess(commandLine, port);
				synchronized (stateLock) {
					socket = null;
				}
			}
		}

		InputStream input;
		OutputStream output;
		synchronized (stateLock) {
			input = socket == null ? process.getInputStream() : socket.getInputStream();
			output = socket == null ? process.getOutputStream() : socket.getOutputStream();
		}
		Launcher<IDebugProtocolServer> launcher = DSPLauncher.createClientLauncher(client, input, output);
		synchronized (stateLock) {
			server = launcher.getRemoteProxy();
		}
		launcher.startListening();

		initialize();
		protocolStarted = true;
		CompletableFuture<Void> launchRequest = launch();
		awaitInitializedEvent(launchRequest);
		await(sendBreakpoints(program, breakpoints));
		if (capabilities != null && Boolean.TRUE.equals(capabilities.getSupportsConfigurationDoneRequest())) {
			await(server.configurationDone(new ConfigurationDoneArguments()));
		}
		await(launchRequest);
	}

	private void startProcess(List<String> commandLine, int port) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(commandLine);
		builder.redirectErrorStream(false);
		Map<String, String> environment = adapter.environment(installDirectory.toString(), program.toString(), port);
		if (!environment.isEmpty()) {
			builder.environment().putAll(environment);
		}
		process = builder.start();
		drainErrors(process);
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
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	private void awaitInitializedEvent(CompletableFuture<Void> launchRequest) throws IOException, InterruptedException {
		CompletableFuture<Object> firstOutcome = CompletableFuture.anyOf(initialized, launchRequest);
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

	/**
	 * Starts the nested debug session an adapter asks for with {@code startDebugging}.
	 *
	 * <p>
	 * Adapters such as the JavaScript adapter launch the debuggee as a child session:
	 * they send a {@code startDebugging} request with the child configuration and expect
	 * the client to open a new session for it. The child session connects to the same
	 * adapter port, inherits the current breakpoints and delegates execution controls
	 * back to the parent while it is the session that stopped.
	 * @param arguments child session request sent by the adapter
	 * @throws IOException when the child session cannot be started
	 * @throws InterruptedException when the calling thread is interrupted
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	private void startChildSession(StartDebuggingRequestArguments arguments) throws IOException, InterruptedException {
		if (arguments == null || arguments.getConfiguration() == null) {
			throw new IOException("The debug adapter requested a child session without a configuration.");
		}
		int port = adapterPort;
		if (port <= 0) {
			throw new IOException("Child debug sessions require a debug adapter reached over TCP.");
		}
		Map<String, Object> configuration = new LinkedHashMap<>(arguments.getConfiguration());
		DapSession child = new DapSession(program, port, configuration, listener, this);
		children.add(child);
		child.start(currentBreakpoints);
		LOGGER.debug("Started a child debug session for {}.", program);
	}

	private RunInTerminalResponse startDebuggee(RunInTerminalRequestArguments arguments) throws IOException {
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
		synchronized (stateLock) {
			if (closed) {
				launched.destroyForcibly();
				throw new IOException("Debug session closed before its program started.");
			}
			if (debuggee != null && debuggee.isAlive()) {
				launched.destroyForcibly();
				throw new IOException("A debugged program is already running.");
			}
			debuggee = launched;
		}
		drainDebuggeeStream(launched.getInputStream(), OutputType.OUTPUT, "simpleide-debuggee-stdout");
		drainDebuggeeStream(launched.getErrorStream(), OutputType.ERROR, "simpleide-debuggee-stderr");
		startDebuggeeInputForwarder(launched);

		RunInTerminalResponse response = new RunInTerminalResponse();
		long processId = launched.pid();
		if (processId <= Integer.MAX_VALUE) {
			response.setProcessId((int) processId);
		}
		return response;
	}

	private void drainDebuggeeStream(InputStream stream, OutputType type, String threadName) {
		Thread drainThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					Output.getInstance().publish(type, line);
				}
			}
			catch (IOException error) {
				if (!closed) {
					LOGGER.debug("Unable to read debugged program output.", error);
				}
			}
		}, threadName);
		drainThread.setDaemon(true);
		drainThread.start();
	}

	private void startDebuggeeInputForwarder(Process launched) {
		Thread inputThread = new Thread(() -> {
			try (BufferedWriter writer = new BufferedWriter(
					new OutputStreamWriter(launched.getOutputStream(), StandardCharsets.UTF_8))) {
				publishWaitingForStdin();
				while (!closed && launched.isAlive()) {
					String line = Input.getInstance().get(1);
					if (line != null) {
						writer.write(line);
						writer.newLine();
						writer.flush();
						waitingForStdinPublished.set(false);
						publishWaitingForStdin();
					}
				}
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
			}
			catch (IOException error) {
				if (!closed && launched.isAlive()) {
					LOGGER.debug("Unable to forward input to the debugged program.", error);
					Output.getInstance().publish(OutputType.ERROR, "Unable to forward input to the debugged program.");
				}
			}
		}, "simpleide-debuggee-stdin");
		inputThread.setDaemon(true);
		inputThread.start();
	}

	private void drainErrors(Process started) {
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
				if (!closed) {
					LOGGER.debug("[DAP] Unable to read adapter diagnostics.", error);
				}
			}
		};
		Thread drainThread = new Thread(drain, "simpleide-dap-stderr");
		drainThread.setDaemon(true);
		drainThread.start();
	}

	private void connect(int port) throws IOException, InterruptedException {
		IOException lastError = null;
		for (int attempt = 0; attempt < 100; attempt++) {
			if (closed) {
				throw new IOException("Debug session was closed before the adapter started.");
			}
			for (String host : LOOPBACK_HOSTS) {
				try {
					Socket candidate = new Socket();
					candidate.connect(new InetSocketAddress(host, port), 500);
					synchronized (stateLock) {
						socket = candidate;
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
		if (adapter != null) {
			arguments.setAdapterID(adapter.getId());
		}
		arguments.setLinesStartAt1(true);
		arguments.setColumnsStartAt1(true);
		arguments.setPathFormat("path");
		arguments.setSupportsRunInTerminalRequest(true);
		Capabilities result = await(server.initialize(arguments));
		synchronized (stateLock) {
			capabilities = result;
		}
	}

	private CompletableFuture<Void> launch() {
		Map<String, Object> arguments;
		if (adapter == null) {
			arguments = new LinkedHashMap<>(externalLaunchArguments);
		}
		else {
			arguments = new LinkedHashMap<>(adapter.launchArguments(program));
			arguments.putIfAbsent("program", program.toString());
			arguments.putIfAbsent("cwd", program.getParent() == null ? "." : program.getParent().toString());
			arguments.putIfAbsent("name", adapter.getName());
		}
		return server.launch(arguments).thenApply(ignored -> null);
	}

	/**
	 * Replaces the line breakpoints of the debugged program.
	 * @param file source file that owns the breakpoints
	 * @param lines one-based line numbers, in any order
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void setBreakpoints(Path file, List<Integer> lines) {
		currentBreakpoints = lines == null ? List.of() : List.copyOf(lines);
		for (DapSession child : children) {
			child.setBreakpoints(file, lines);
		}
		sendBreakpoints(file, lines).whenComplete((response, error) -> {
			if (error != null) {
				LOGGER.warn("Unable to set debug breakpoints for {}.", file, error);
				return;
			}
			if (response != null && response.getBreakpoints() != null) {
				for (Breakpoint breakpoint : response.getBreakpoints()) {
					if (Boolean.FALSE.equals(breakpoint.isVerified())) {
						LOGGER.debug("Debug adapter did not verify a breakpoint at line {}.", breakpoint.getLine());
					}
				}
			}
		});
	}

	private CompletableFuture<SetBreakpointsResponse> sendBreakpoints(Path file, List<Integer> lines) {
		IDebugProtocolServer activeServer;
		synchronized (stateLock) {
			activeServer = server;
		}
		if (activeServer == null || file == null) {
			return CompletableFuture.completedFuture(null);
		}
		Source source = new Source();
		source.setName(file.getFileName().toString());
		source.setPath(file.toString());
		List<SourceBreakpoint> sourceBreakpoints = new ArrayList<>();
		for (Integer line : lines == null ? List.<Integer>of() : lines) {
			if (line != null && line > 0) {
				SourceBreakpoint breakpoint = new SourceBreakpoint();
				breakpoint.setLine(line);
				sourceBreakpoints.add(breakpoint);
			}
		}
		SetBreakpointsArguments arguments = new SetBreakpointsArguments();
		arguments.setSource(source);
		arguments.setBreakpoints(sourceBreakpoints.toArray(SourceBreakpoint[]::new));
		return activeServer.setBreakpoints(arguments);
	}

	/**
	 * Resumes execution.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void resume() {
		DapSession delegate = controlTarget.get();
		if (delegate != null && delegate != this) {
			delegate.resume();
			return;
		}
		sendWhenStopped(activeThread -> {
			ContinueArguments arguments = new ContinueArguments();
			arguments.setThreadId(activeThread);
			return server.continue_(arguments).thenApply(ignored -> null);
		});
	}

	/**
	 * Pauses execution.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void pause() {
		DapSession delegate = controlTarget.get();
		if (delegate != null && delegate != this) {
			delegate.pause();
			return;
		}
		sendUsingThread(activeThread -> {
			PauseArguments arguments = new PauseArguments();
			arguments.setThreadId(activeThread);
			return server.pause(arguments);
		});
	}

	/**
	 * Steps over the current statement.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void stepOver() {
		DapSession delegate = controlTarget.get();
		if (delegate != null && delegate != this) {
			delegate.stepOver();
			return;
		}
		sendWhenStopped(activeThread -> {
			NextArguments arguments = new NextArguments();
			arguments.setThreadId(activeThread);
			return server.next(arguments);
		});
	}

	/**
	 * Steps into the next call.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void stepInto() {
		DapSession delegate = controlTarget.get();
		if (delegate != null && delegate != this) {
			delegate.stepInto();
			return;
		}
		sendWhenStopped(activeThread -> {
			StepInArguments arguments = new StepInArguments();
			arguments.setThreadId(activeThread);
			return server.stepIn(arguments);
		});
	}

	/**
	 * Steps out of the current call.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public void stepOut() {
		DapSession delegate = controlTarget.get();
		if (delegate != null && delegate != this) {
			delegate.stepOut();
			return;
		}
		sendWhenStopped(activeThread -> {
			StepOutArguments arguments = new StepOutArguments();
			arguments.setThreadId(activeThread);
			return server.stepOut(arguments);
		});
	}

	/**
	 * Returns whether the adapter reported a configuration-done capability.
	 * @return {@code true} when configurationDone is supported
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean supportsConfigurationDone() {
		synchronized (stateLock) {
			return capabilities != null && Boolean.TRUE.equals(capabilities.getSupportsConfigurationDoneRequest());
		}
	}

	private void sendWhenStopped(java.util.function.IntFunction<CompletableFuture<Void>> request) {
		if (!stopped) {
			Output.getInstance().publish(OutputType.DEBUG, "Execution is running; pause before stepping.");
			return;
		}
		sendUsingThread(request);
	}

	private void sendUsingThread(java.util.function.IntFunction<CompletableFuture<Void>> request) {
		IDebugProtocolServer activeServer;
		synchronized (stateLock) {
			activeServer = server;
		}
		if (activeServer == null || closed) {
			return;
		}
		int activeThread = threadId;
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
			threadId = resolved;
			sendRequest(request, resolved);
		});
	}

	private void sendRequest(java.util.function.IntFunction<CompletableFuture<Void>> request, int activeThread) {
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
			return UNKNOWN_THREAD;
		}
		for (org.eclipse.lsp4j.debug.Thread thread : response.getThreads()) {
			if (thread != null && thread.getId() >= 0) {
				return thread.getId();
			}
		}
		return UNKNOWN_THREAD;
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

	/**
	 * Ends the session, terminating the debugged program and the adapter process.
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	@Override
	public void close() {
		synchronized (stateLock) {
			if (closed) {
				return;
			}
			closed = true;
		}
		for (DapSession child : children) {
			child.close();
		}
		children.clear();
		IDebugProtocolServer activeServer = server;
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
		closeQuietly(socket);
		terminateProcess(debuggee);
		debuggee = null;
		terminateProcess(process);
		process = null;
		notifyTerminated();
		if (protocolStarted) {
			publishFinished();
		}
	}

	private void publishWaitingForStdin() {
		if (waitingForStdinPublished.compareAndSet(false, true)) {
			Output.getInstance().publish(OutputType.OUTPUT, Output.WAITING_FOR_STDIN);
		}
	}

	private void publishFinished() {
		if (parent == null && finishedPublished.compareAndSet(false, true)) {
			Output.getInstance().publish(OutputType.OUTPUT, Output.FINISHED);
		}
	}

	private static void terminateProcess(Process activeProcess) {
		if (activeProcess == null || !activeProcess.isAlive()) {
			return;
		}
		activeProcess.destroy();
		Thread terminator = new Thread(() -> {
			try {
				if (!activeProcess.waitFor(2, TimeUnit.SECONDS)) {
					activeProcess.destroyForcibly();
				}
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				activeProcess.destroyForcibly();
			}
		}, "simpleide-dap-process-stop");
		terminator.setDaemon(true);
		terminator.start();
	}

	private void notifyTerminated() {
		if (terminationNotified.compareAndSet(false, true)) {
			listener.onTerminated();
		}
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

	/**
	 * Client endpoint that translates adapter events into shared output and listener
	 * calls.
	 */
	private final class Client implements IDebugProtocolClient {

		@Override
		public void initialized() {
			if (parent == null) {
				Output.getInstance().publish(OutputType.OUTPUT, Output.INITIALIZED);
			}
			initialized.complete(null);
		}

		@Override
		public void output(OutputEventArguments arguments) {
			if (arguments == null || arguments.getOutput() == null) {
				return;
			}
			OutputType type = "stderr".equalsIgnoreCase(arguments.getCategory()) ? OutputType.ERROR
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
			stopped = true;
			if (parent != null) {
				// Execution controls follow the session that actually stopped, which for
				// adapters that launch the debuggee as a child session is the child.
				parent.controlTarget.set(DapSession.this);
			}
			String reason = arguments == null || arguments.getReason() == null ? "unknown" : arguments.getReason();
			if (arguments != null && arguments.getThreadId() != null && arguments.getThreadId() >= 0) {
				threadId = arguments.getThreadId();
			}
			Output.getInstance().publish(OutputType.DEBUG, "Stopped: " + reason);
			listener.onStopped(threadId < 0 ? 0 : threadId, reason);
		}

		@Override
		public void continued(org.eclipse.lsp4j.debug.ContinuedEventArguments arguments) {
			stopped = false;
			if (parent != null) {
				parent.controlTarget.compareAndSet(DapSession.this, null);
			}
			listener.onContinued();
		}

		@Override
		public void terminated(TerminatedEventArguments arguments) {
			stopped = false;
			if (parent != null) {
				parent.controlTarget.compareAndSet(DapSession.this, null);
			}
			Output.getInstance().publish(OutputType.DEBUG, "Debug session terminated.");
			notifyTerminated();
			publishFinished();
		}

		/**
		 * Starts the debuggee requested by the adapter with its streams connected to the
		 * I/O tab.
		 * @param arguments debuggee command and working directory
		 * @return the launched process information
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		@Override
		public CompletableFuture<Void> startDebugging(StartDebuggingRequestArguments arguments) {
			CompletableFuture<Void> started = new CompletableFuture<>();
			Thread launcher = new Thread(() -> {
				try {
					startChildSession(arguments);
					started.complete(null);
				}
				catch (InterruptedException error) {
					Thread.currentThread().interrupt();
					started.completeExceptionally(error);
				}
				catch (IOException | RuntimeException error) {
					started.completeExceptionally(error);
				}
			}, "simpleide-dap-child-session");
			launcher.setDaemon(true);
			launcher.start();
			return started;
		}

		@Override
		public CompletableFuture<RunInTerminalResponse> runInTerminal(RunInTerminalRequestArguments arguments) {
			try {
				return CompletableFuture.completedFuture(startDebuggee(arguments));
			}
			catch (IOException | RuntimeException error) {
				return CompletableFuture.failedFuture(error);
			}
		}

	}

}
