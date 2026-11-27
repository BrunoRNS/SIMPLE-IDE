package io.simpleide.lsp.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.SimpleIDEVersion;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * Owns the language server process, its launcher and its shutdown lifecycle.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LspServerConnection {

	private static final Logger LOGGER = LoggerFactory.getLogger(LspServerConnection.class);

	private static final long INITIALIZATION_TIMEOUT_SECONDS = 180;

	private static final int MAX_RECENT_ERROR_LINES = 20;

	private final LspClient client;

	private final CompletableFuture<Void> initialized;

	private final CompletableFuture<Void> shutdownComplete;

	private final ManagedThreadPool workerPool;

	private final RunnableNode runtimeNode;

	private final Map<String, Object> initializationOptions;

	private final BooleanSupplier closed;

	private final Runnable closeCallback;

	private final Deque<String> recentErrorLines = new ConcurrentLinkedDeque<>();

	private volatile ManagedThreadPool errorReaderPool;

	private volatile ExecutorService lspExecutor;

	private volatile ScheduledFuture<?> initializationTimeoutTask;

	private volatile Process process;

	private volatile LanguageServer server;

	private volatile boolean initializationSubmitted;

	private volatile LspCapabilities.ServerCapabilitiesSnapshot serverCapabilities = LspCapabilities.ServerCapabilitiesSnapshot.NONE;

	LspServerConnection(LspClient client, CompletableFuture<Void> initialized, CompletableFuture<Void> shutdownComplete,
			ManagedThreadPool workerPool, RunnableNode runtimeNode, Map<String, Object> initializationOptions,
			BooleanSupplier closed, Runnable closeCallback) {
		this.client = client;
		this.initialized = initialized;
		this.shutdownComplete = shutdownComplete;
		this.workerPool = workerPool;
		this.runtimeNode = runtimeNode;
		this.initializationOptions = initializationOptions;
		this.closed = closed;
		this.closeCallback = closeCallback;
	}

	void start(List<String> command, Path workspaceRoot, Map<String, String> environment) {
		if (closed.getAsBoolean()) {
			return;
		}

		try {
			ProcessBuilder processBuilder = new ProcessBuilder(command);
			processBuilder.environment().putAll(environment);
			if (workspaceRoot != null && Files.isDirectory(workspaceRoot)) {
				processBuilder.directory(workspaceRoot.toFile());
			}

			Process startedProcess = processBuilder.start();
			process = startedProcess;
			if (closed.getAsBoolean()) {
				closeProcess();
				return;
			}

			startErrorStreamReader(startedProcess);
			Launcher<LanguageServer> launcher;
			boolean aborted;
			synchronized (this) {
				aborted = closed.getAsBoolean();
				if (!aborted) {
					lspExecutor = createLspExecutor();
					launcher = LSPLauncher.createClientLauncher(client, startedProcess.getInputStream(),
							startedProcess.getOutputStream(), lspExecutor, Function.identity());
					server = launcher.getRemoteProxy();
					launcher.startListening();
				}
			}
			if (aborted) {
				closeProcess();
				return;
			}

			startedProcess.onExit().thenAccept(exited -> {
				if (closed.getAsBoolean()) {
					return;
				}
				try {
					ThreadMaster.getInstance().register(new RunnableNode(runtimeNode, () -> {
						if (!closed.getAsBoolean() && !initialized.isDone()) {
							LspServerStartupException error = new LspServerStartupException(exited.exitValue());
							String output = recentErrorOutput();
							if (!output.isBlank()) {
								LOGGER.error("Language server output before exit:{}{}", System.lineSeparator(), output);
							}
							if (error.isJvmCompatibilityFailure()) {
								LOGGER
									.warn("Language server exited with code 13 before initialization. This usually means "
											+ "the Java runtime used to launch it is older than the server requires. "
											+ "On institutional machines with a fixed Java runtime this is a known issue; "
											+ "Simple IDE will try a compatible language server version when available.");
							}
							if (initialized.completeExceptionally(error)) {
								LOGGER.error("Language server exited before initialization.", error);
								closeCallback.run();
							}
						}
						else if (!closed.getAsBoolean()) {
							LOGGER.warn("Language server process exited with code {}.", exited.exitValue());
							closeCallback.run();
						}
					}));
				}
				catch (RuntimeException error) {
					LOGGER.error("Unable to schedule language server exit handling.", error);
				}
			});

			if (closed.getAsBoolean()) {
				closeProcess();
				return;
			}
			initializationSubmitted = true;
			initialize(workspaceRoot);
		}
		catch (IOException | RuntimeException error) {
			if (closed.getAsBoolean()) {
				LOGGER.debug("Language server startup stopped after the client was closed.", error);
				closeProcess();
				return;
			}
			initialized.completeExceptionally(error);
			LOGGER.error("Unable to start language server.", error);
			closeCallback.run();
		}
	}

	LanguageServer getServer() {
		return server;
	}

	LspCapabilities.ServerCapabilitiesSnapshot getCapabilities() {
		return serverCapabilities;
	}

	boolean isInitializationSubmitted() {
		return initializationSubmitted;
	}

	boolean isProcessAlive() {
		Process child = process;
		return child != null && child.isAlive();
	}

	void shutdown() {
		LanguageServer activeServer = server;
		boolean interrupted = false;
		try {
			if (initialized.isDone() && !initialized.isCompletedExceptionally() && activeServer != null) {
				try {
					activeServer.shutdown().get(2, TimeUnit.SECONDS);
				}
				catch (InterruptedException error) {
					interrupted = true;
				}
				catch (ExecutionException | TimeoutException | RuntimeException ignored) {
				}
				try {
					activeServer.exit();
				}
				catch (RuntimeException ignored) {
				}
			}
		}
		finally {
			closeProcess();
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void initialize(Path workspaceRoot) {
		InitializeParams params = new InitializeParams();

		params.setProcessId(Math.toIntExact(ProcessHandle.current().pid()));
		params.setClientInfo(new ClientInfo("SIMPLE-IDE", SimpleIDEVersion.VERSION));
		params.setCapabilities(LspCapabilities.clientCapabilities());
		if (!initializationOptions.isEmpty()) {
			params.setInitializationOptions(initializationOptions);
		}

		if (workspaceRoot != null) {
			Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
			String rootUri = normalizedRoot.toUri().toString();
			String folderName = normalizedRoot.getFileName() != null ? normalizedRoot.getFileName().toString()
					: normalizedRoot.toString();

			params.setWorkspaceFolders(List.of(new WorkspaceFolder(rootUri, folderName)));
		}

		initializationTimeoutTask = ThreadMaster.getInstance().schedule(runtimeNode, () -> {
			TimeoutException error = new TimeoutException("Language server initialization timed out.");
			if (initialized.completeExceptionally(error)) {
				LOGGER.error("Unable to initialize language server.", error);
				closeCallback.run();
			}
		}, INITIALIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		server.initialize(params).whenComplete((result, error) -> {
			ScheduledFuture<?> timeoutTask = initializationTimeoutTask;
			if (timeoutTask != null) {
				timeoutTask.cancel(false);
			}
			if (closed.getAsBoolean() || initialized.isDone()) {
				return;
			}
			if (error != null) {
				initialized.completeExceptionally(error);
				LOGGER.error("Unable to initialize language server.", error);
				closeCallback.run();
				return;
			}
			try {
				if (result == null || result.getCapabilities() == null) {
					throw new IllegalStateException("Language server returned no capabilities.");
				}
				serverCapabilities = LspCapabilities.snapshot(result);
				server.initialized(new InitializedParams());
				initialized.complete(null);
				LOGGER.info("Language server initialized for workspace {}.",
						workspaceRoot == null ? "(none)" : workspaceRoot);
			}
			catch (RuntimeException initializationError) {
				initialized.completeExceptionally(initializationError);
				LOGGER.error("Unable to finish language server initialization.", initializationError);
				closeCallback.run();
			}
		});
	}

	private void startErrorStreamReader(Process serverProcess) {
		ManagedThreadPool pool = ThreadMaster.getInstance().createThreadPool("lsp-stderr-" + serverProcess.pid(), 1);
		errorReaderPool = pool;
		try {
			pool.execute(() -> {
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(serverProcess.getErrorStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null) {
						recentErrorLines.addLast(line);
						while (recentErrorLines.size() > MAX_RECENT_ERROR_LINES) {
							recentErrorLines.removeFirst();
						}
						LOGGER.debug("[LSP server] {}", line);
					}
				}
				catch (IOException error) {
					if (!closed.getAsBoolean()) {
						LOGGER.warn("Unable to read language server output.", error);
					}
				}
			});
		}
		catch (RuntimeException error) {
			errorReaderPool = null;
			pool.close();
			throw error;
		}
	}

	private String recentErrorOutput() {
		return String.join(System.lineSeparator(), recentErrorLines);
	}

	private ExecutorService createLspExecutor() {
		return workerPool == null ? ThreadMaster.getInstance().createExecutorService(runtimeNode)
				: workerPool.createExecutorService();
	}

	private void closeProcess() {
		Process child = process;
		try {
			if (child != null) {
				child.destroy();
				if (!waitFor(child, 2, TimeUnit.SECONDS)) {
					child.destroyForcibly();
					if (!waitFor(child, 2, TimeUnit.SECONDS)) {
						LOGGER.warn("Unable to terminate language server process.");
					}
				}
				try {
					child.getErrorStream().close();
				}
				catch (IOException error) {
					LOGGER.debug("Unable to close language server stderr.", error);
				}
			}
		}
		finally {
			ExecutorService executor = lspExecutor;
			lspExecutor = null;
			if (executor != null) {
				executor.shutdownNow();
			}
			ScheduledFuture<?> timeoutTask = initializationTimeoutTask;
			initializationTimeoutTask = null;
			if (timeoutTask != null) {
				timeoutTask.cancel(false);
			}
			ManagedThreadPool stderrPool = errorReaderPool;
			errorReaderPool = null;
			if (stderrPool != null) {
				stderrPool.close();
			}
			try {
				ThreadMaster.getInstance().removeNodeCascade(runtimeNode);
			}
			finally {
				shutdownComplete.complete(null);
			}
		}
	}

	private boolean waitFor(Process child, long timeout, TimeUnit unit) {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		boolean interrupted = false;
		try {
			while (true) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) {
					return !child.isAlive();
				}
				try {
					return child.waitFor(remaining, TimeUnit.NANOSECONDS);
				}
				catch (InterruptedException error) {
					interrupted = true;
				}
			}
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
