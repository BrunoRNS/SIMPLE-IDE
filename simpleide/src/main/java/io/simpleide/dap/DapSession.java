package io.simpleide.dap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;

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

	static final ManagedThreadPool LIFECYCLE_POOL = ThreadMaster.getInstance().createThreadPool("dap-lifecycle", 4);

	/**
	 * Sentinel for a thread that the adapter did not report yet. Thread {@code 0} is a
	 * valid identifier for some adapters, so it cannot double as "unknown".
	 */
	static final int UNKNOWN_THREAD = -1;

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
		 */
		default void onStopped(int threadId, String reason) {
		}

		/**
		 * Called when execution resumes.
		 */
		default void onContinued() {
		}

		/**
		 * Called when the debug session ends.
		 */
		default void onTerminated() {
		}

	}

	final DapAdapter adapter;

	final Path installDirectory;

	final Path program;

	final Listener listener;

	final int externalPort;

	final Map<String, Object> externalLaunchArguments;

	final DapProtocolClient client = new DapProtocolClient(this);

	final Object stateLock = new Object();

	final DapSession parent;

	final List<DapSession> children = new CopyOnWriteArrayList<>();

	final AtomicReference<DapSession> controlTarget = new AtomicReference<>();

	final AtomicReference<DapSessionSnapshot> sessionState = new AtomicReference<>(DapSessionSnapshot.initial());

	Process process;

	Process debuggee;

	java.net.Socket socket;

	IDebugProtocolServer server;

	Capabilities capabilities;

	final AtomicBoolean terminationNotified = new AtomicBoolean();

	final AtomicBoolean finishedPublished = new AtomicBoolean();

	final AtomicBoolean waitingForStdinPublished = new AtomicBoolean();

	CompletableFuture<Void> initialized = new CompletableFuture<>();

	private final DapSessionStarter starter = new DapSessionStarter(this);

	final DapProgramRunner programRunner = new DapProgramRunner(this);

	private final DapExecutionControl control = new DapExecutionControl(this);

	/**
	 * Creates a session that has not started yet.
	 * @param adapter adapter definition
	 * @param installDirectory directory where the adapter is installed
	 * @param program program to debug
	 * @param listener listener for execution state changes, may be {@code null}
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
	 */
	public void start(List<Integer> breakpoints) throws IOException, InterruptedException {
		starter.start(breakpoints);
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
	 */
	void startChildSession(StartDebuggingRequestArguments arguments) throws IOException, InterruptedException {
		if (arguments == null || arguments.getConfiguration() == null) {
			throw new IOException("The debug adapter requested a child session without a configuration.");
		}
		DapSessionSnapshot state = sessionState.get();
		int port = state.adapterPort();
		if (port <= 0) {
			throw new IOException("Child debug sessions require a debug adapter reached over TCP.");
		}
		Map<String, Object> configuration = new LinkedHashMap<>(arguments.getConfiguration());
		DapSession child = new DapSession(program, port, configuration, listener, this);
		children.add(child);
		child.start(state.breakpoints());
		LOGGER.debug("Started a child debug session for {}.", program);
	}

	RunInTerminalResponse startDebuggee(RunInTerminalRequestArguments arguments) throws IOException {
		return programRunner.start(arguments);
	}

	/**
	 * Replaces the line breakpoints of the debugged program.
	 * @param file source file that owns the breakpoints
	 * @param lines one-based line numbers, in any order
	 */
	public void setBreakpoints(Path file, List<Integer> lines) {
		List<Integer> updatedBreakpoints = lines == null ? List.of() : List.copyOf(lines);
		sessionState.updateAndGet(state -> state.withBreakpoints(updatedBreakpoints));
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

	CompletableFuture<SetBreakpointsResponse> sendBreakpoints(Path file, List<Integer> lines) {
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
	 */
	public void resume() {
		control.resume();
	}

	/**
	 * Pauses execution.
	 */
	public void pause() {
		control.pause();
	}

	/**
	 * Steps over the current statement.
	 */
	public void stepOver() {
		control.stepOver();
	}

	/**
	 * Steps into the next call.
	 */
	public void stepInto() {
		control.stepInto();
	}

	/**
	 * Steps out of the current call.
	 */
	public void stepOut() {
		control.stepOut();
	}

	/**
	 * Returns whether the adapter reported a configuration-done capability.
	 * @return {@code true} when configurationDone is supported
	 */
	public boolean supportsConfigurationDone() {
		return control.supportsConfigurationDone();
	}

	/**
	 * Ends the session, terminating the debugged program and the adapter process.
	 */
	@Override
	public void close() {
		DapSessionLifecycle.close(this);
	}

	void publishFinished() {
		if (parent == null && finishedPublished.compareAndSet(false, true)) {
			Output.getInstance().publish(OutputType.OUTPUT, Output.FINISHED);
		}
	}

	void notifyTerminated() {
		if (terminationNotified.compareAndSet(false, true)) {
			listener.onTerminated();
		}
	}

}
