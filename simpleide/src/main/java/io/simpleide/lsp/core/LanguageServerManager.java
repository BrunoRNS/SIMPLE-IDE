package io.simpleide.lsp.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.client.LspServerStartupException;
import io.simpleide.lsp.dowload.LspManager;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.ThreadTaskScheduler;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.utils.JavaRuntime;

/**
 * Coordinates language server processes for documents and workspaces.
 *
 * <p>
 * The manager detects a workspace root from build markers, shares one {@link LspClient}
 * per language and workspace root, and forwards diagnostics to the handler registered for
 * each document URI. Startup failures are reported as {@link LspStatus} events, and a
 * Java runtime compatibility failure can trigger a retry with an older server version.
 * Closing the manager cancels startup tasks and shuts down all clients.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public final class LanguageServerManager implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerManager.class);

	private static final Set<String> WORKSPACE_MARKERS = Set.of("pom.xml", "build.gradle", "build.gradle.kts",
			"settings.gradle", "settings.gradle.kts", "Cargo.toml", "package.json", "pyproject.toml", "setup.py",
			"go.mod", "compile_commands.json");

	private final Map<ServerKey, ServerEntry> servers = new ConcurrentHashMap<>();

	private final Consumer<LspStatus> statusListener;

	private final Function<Path, Path> workspaceRootResolver;

	private final Function<String, Map<String, String>> environmentProvider;

	private final ManagedThreadPool workerPool;

	private volatile boolean closed;

	/**
	 * Creates a manager that ignores status updates.
	 */
	public LanguageServerManager() {
		this(ignored -> {
		});
	}

	/**
	 * Creates a manager that reports installation and startup status to a listener.
	 * @param statusListener consumer for status events
	 */
	public LanguageServerManager(Consumer<LspStatus> statusListener) {
		this(statusListener, LanguageServerManager::findWorkspaceRoot, LanguageServerManager::getEnvironment);
	}

	LanguageServerManager(Consumer<LspStatus> statusListener, Function<Path, Path> workspaceRootResolver,
			Function<String, Map<String, String>> environmentProvider) {
		this.statusListener = Objects.requireNonNull(statusListener, "statusListener");
		this.workspaceRootResolver = Objects.requireNonNull(workspaceRootResolver, "workspaceRootResolver");
		this.environmentProvider = Objects.requireNonNull(environmentProvider, "environmentProvider");
		workerPool = ThreadMaster.getInstance().createThreadPool("lsp", ThreadTaskScheduler.WORKER_COUNT);
	}

	/**
	 * Connects a document to a shared language server, starting it when necessary.
	 * @param document document to connect
	 * @param diagnosticsHandler consumer for diagnostics of this document
	 * @return a future completed with the initialized client, or failed when startup
	 * fails
	 */
	public CompletableFuture<LspClient> connect(Document document,
			Consumer<PublishDiagnosticsParams> diagnosticsHandler) {

		Objects.requireNonNull(document, "document");
		Objects.requireNonNull(diagnosticsHandler, "diagnosticsHandler");
		if (closed) {
			return CompletableFuture.failedFuture(new IllegalStateException("Language server manager is closed."));
		}
		CompletableFuture<LspClient> connection = new CompletableFuture<>();
		AtomicBoolean setupFinished = new AtomicBoolean();
		try {
			workerPool.execute(() -> {
				try {
					connectInBackground(document, diagnosticsHandler, connection);
				}
				catch (RuntimeException error) {
					connection.completeExceptionally(error);
				}
				finally {
					setupFinished.set(true);
				}
			}, () -> {
				if (!setupFinished.get()) {
					connection.completeExceptionally(
							new CancellationException("Language server connection setup was cancelled."));
				}
			});
		}
		catch (RuntimeException error) {
			connection.completeExceptionally(error);
		}
		return connection;
	}

	private void connectInBackground(Document document, Consumer<PublishDiagnosticsParams> diagnosticsHandler,
			CompletableFuture<LspClient> connection) {
		if (!document.isOpened()) {
			connection.completeExceptionally(new CancellationException("Document was closed before LSP setup."));
			return;
		}
		Path root;
		Map<String, String> environment;
		try {
			root = workspaceRootResolver.apply(document.getPath());
			environment = environmentProvider.apply(document.getLanguageId());
		}
		catch (RuntimeException error) {
			connection.completeExceptionally(error);
			return;
		}

		ServerKey key = new ServerKey(document.getLanguageId(), root);
		ServerEntry entry;
		synchronized (this) {
			if (closed) {
				connection.completeExceptionally(new IllegalStateException("Language server manager is closed."));
				return;
			}
			if (!document.isOpened()) {
				connection.completeExceptionally(new CancellationException("Document was closed before LSP startup."));
				return;
			}
			try {
				entry = servers.computeIfAbsent(key,
						ignored -> new ServerEntry(document.getLanguageId(), root, environment, statusId(key)));
			}
			catch (RuntimeException error) {
				connection.completeExceptionally(error);
				return;
			}
			entry.diagnosticsHandlers.put(document.getUri(), diagnosticsHandler);
			if (!document.isOpened()) {
				entry.diagnosticsHandlers.remove(document.getUri(), diagnosticsHandler);
				connection.completeExceptionally(new CancellationException("Document was closed before LSP startup."));
				return;
			}
		}
		entry.client.whenComplete((client, error) -> {
			if (error != null || client == null) {
				servers.remove(key, entry);
			}
			if (error != null) {
				connection.completeExceptionally(unwrapFailure(error));
			}
			else {
				connection.complete(client);
			}
		});
	}

	/**
	 * Removes the diagnostics handler registered for a document.
	 * @param document document to disconnect
	 */
	public void disconnect(Document document) {
		for (ServerEntry entry : servers.values()) {
			entry.diagnosticsHandlers.remove(document.getUri());
		}
	}

	/**
	 * Cancels startup tasks and closes every managed language server client.
	 */
	@Override
	public void close() {
		List<ServerEntry> entries;
		synchronized (this) {
			if (closed) {
				return;
			}
			closed = true;
			entries = new ArrayList<>(servers.values());
		}
		List<LspClient> clients = new ArrayList<>();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		for (ServerEntry entry : entries) {
			LspClient activeClient = entry.activeClient();
			try {
				ThreadMaster.getInstance().removeNodeCascade(entry.startupNode);
			}
			catch (RuntimeException error) {
				LOGGER.warn("Unable to unregister language server startup task.", error);
			}
			if (!entry.client.isDone()) {
				entry.client.completeExceptionally(new CancellationException("Language server manager is closed."));
			}
			LspClient client = awaitClient(entry.client, deadline);
			if (client == null) {
				client = activeClient == null ? entry.activeClient() : activeClient;
			}
			if (client != null) {
				clients.add(client);
			}
			publishStatus(entry.statusId, entry.languageId, LspStatus.State.IDLE);
		}
		servers.clear();

		for (LspClient client : clients) {
			client.close();
		}
		for (LspClient client : clients) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0 || !client.awaitShutdown(remaining, TimeUnit.NANOSECONDS)) {
				LOGGER.warn("Language server did not shut down within the grace period.");
			}
		}
		workerPool.close();
	}

	private LspClient awaitClient(CompletableFuture<LspClient> client, long deadline) {
		long remaining = deadline - System.nanoTime();
		if (remaining <= 0) {
			return completedClient(client);
		}
		try {
			return client.get(remaining, TimeUnit.NANOSECONDS);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return completedClient(client);
		}
		catch (CancellationException | ExecutionException | TimeoutException error) {
			return completedClient(client);
		}
	}

	private LspClient completedClient(CompletableFuture<LspClient> client) {
		if (!client.isDone() || client.isCompletedExceptionally() || client.isCancelled()) {
			return null;
		}
		try {
			return client.getNow(null);
		}
		catch (RuntimeException error) {
			return null;
		}
	}

	private String statusId(ServerKey key) {
		return key.languageId() + "@" + key.workspaceRoot();
	}

	private Throwable unwrapFailure(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException)
				&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	private void publishStatus(String id, String serverName, LspStatus.State state) {
		statusListener.accept(new LspStatus(id, serverName, state));
	}

	private static Map<String, String> getEnvironment(String languageId) {
		if ("java".equals(languageId)) {
			String configuredHome = System.getProperty(JavaRuntime.LSP_JAVA_HOME_PROPERTY);
			if (configuredHome != null && !configuredHome.isBlank()) {
				Path path = Path.of(configuredHome).toAbsolutePath().normalize();
				if (!Files.isDirectory(path)) {
					throw new IllegalArgumentException("Configured LSP Java home is not a directory: " + path);
				}
			}
		}
		return JavaRuntime.environment();
	}

	private static Path findWorkspaceRoot(Path documentPath) {
		Path directory = documentPath.toAbsolutePath().normalize().getParent();
		if (directory == null) {
			return null;
		}
		for (Path current = directory; current != null; current = current.getParent()) {
			for (String marker : WORKSPACE_MARKERS) {
				if (Files.exists(current.resolve(marker))) {
					return current;
				}
			}
		}
		return directory;
	}

	/**
	 * Startup state and diagnostics handlers for one language and workspace root.
	 *
	 * @author LuisOtavio13
	 * @since v1.0.0beta
	 */
	private final class ServerEntry {

		private final Map<String, Consumer<PublishDiagnosticsParams>> diagnosticsHandlers = new ConcurrentHashMap<>();

		private final CompletableFuture<LspClient> client = new CompletableFuture<>();

		private final String languageId;

		private final Path root;

		private final Map<String, String> environment;

		private final String statusId;

		private final NodePattern startupNode;

		private volatile LspClient activeClient;

		private volatile boolean startupFinished;

		private ServerEntry(String languageId, Path root, Map<String, String> environment, String statusId) {
			this.languageId = languageId;
			this.root = root;
			this.environment = environment;
			this.statusId = statusId;
			startupNode = workerPool.execute(this::start, this::onStartupNodeClosed);
			client.whenComplete((lspClient, error) -> {
				if (error != null) {
					if (!closed) {
						LOGGER.error("Unable to start language server {} for workspace {}.", languageId, root, error);
						publishStatus(statusId, languageId, LspStatus.State.FAILED);
					}
				}
				else if (closed && lspClient != null) {
					lspClient.close();
				}
			});

		}

		private void start() {
			try {
				if (closed) {
					publishStatus(statusId, languageId, LspStatus.State.IDLE);
					client.complete(null);
					return;
				}
				List<String> command = LspManager.getComandRun(languageId, root, statusId, statusListener);
				if (command.isEmpty() || closed) {
					publishStatus(statusId, languageId, LspStatus.State.IDLE);
					client.complete(null);
					return;
				}
				startAttempt(command, 0);
			}
			catch (RuntimeException error) {
				client.completeExceptionally(error);
			}
			finally {
				startupFinished = true;
			}
		}

		private void startAttempt(List<String> command, int attempt) {
			if (closed) {
				closeActiveClient();
				client.completeExceptionally(new CancellationException("Language server startup was cancelled."));
				return;
			}

			publishStatus(statusId, languageId, LspStatus.State.STARTING);
			LspClient lspClient;
			try {
				lspClient = new LspClient(command, root, diagnostics -> {
					Consumer<PublishDiagnosticsParams> handler = diagnosticsHandlers.get(diagnostics.getUri());
					if (handler != null) {
						handler.accept(diagnostics);
					}
				}, environment, workerPool);
			}
			catch (IOException | RuntimeException error) {
				handleStartupFailure(error, attempt);
				return;
			}

			activeClient = lspClient;
			if (closed) {
				closeActiveClient();
				client.completeExceptionally(new CancellationException("Language server startup was cancelled."));
				return;
			}

			lspClient.getInitialized().whenComplete((ignored, error) -> {
				if (error == null) {
					if (closed) {
						lspClient.close();
						return;
					}
					publishStatus(statusId, languageId, LspStatus.State.RUNNING);
					client.complete(lspClient);
					if (closed) {
						lspClient.close();
					}
				}
				else {
					handleStartupFailure(unwrapCompletionException(error), attempt);
				}
			});
		}

		private void handleStartupFailure(Throwable error, int attempt) {
			if (closed) {
				closeActiveClient();
				return;
			}

			boolean jvmCompatibilityFailure = error instanceof LspServerStartupException startup
					&& startup.isJvmCompatibilityFailure();
			if (jvmCompatibilityFailure) {
				if (attempt == 0) {
					List<String> fallbackCommand = new ArrayList<>();
					try {
						fallbackCommand = LspManager.getFallbackCommandRun(languageId, root, statusId, statusListener);
					}
					catch (RuntimeException fallbackError) {
						error.addSuppressed(fallbackError);
					}
					if (closed) {
						closeActiveClient();
						return;
					}
					if (!fallbackCommand.isEmpty()) {
						LOGGER.warn("Language server {} for workspace {} exited with code 13 before "
								+ "initialization; retrying with a compatible version. On institutional machines "
								+ "with a fixed Java runtime this is a known issue.", languageId, root);
						closeActiveClient();
						startAttempt(fallbackCommand, attempt + 1);
						return;
					}
				}
				LOGGER.warn("Language server {} for workspace {} cannot run with the Java runtime in use. "
						+ "No compatible fallback is available. On institutional machines with a fixed Java "
						+ "runtime this is a known issue; install a compatible JDK or set "
						+ "-Dsimpleide.lsp.javaHome.", languageId, root);
			}

			closeActiveClient();
			client.completeExceptionally(error);
		}

		private Throwable unwrapCompletionException(Throwable error) {
			Throwable cause = error;
			while ((cause instanceof CompletionException || cause instanceof ExecutionException)
					&& cause.getCause() != null) {
				cause = cause.getCause();
			}
			return cause;
		}

		private void closeActiveClient() {
			LspClient current = activeClient;
			activeClient = null;
			if (current != null) {
				current.close();
			}
		}

		private LspClient activeClient() {
			return activeClient;
		}

		private void onStartupNodeClosed() {
			if (!startupFinished) {
				if (!client.isDone()) {
					client.completeExceptionally(new CancellationException("Language server startup was cancelled."));
				}
				closeActiveClient();
			}
		}

	}

	/**
	 * Key that identifies a shared server by language and workspace root.
	 *
	 * @param languageId LSP language identifier
	 * @param workspaceRoot detected workspace root
	 * @author LuisOtavio13
	 * @since v1.0.0beta
	 */
	private record ServerKey(String languageId, Path workspaceRoot) {
	}

}
