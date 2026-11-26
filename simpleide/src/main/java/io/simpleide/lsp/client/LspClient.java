package io.simpleide.lsp.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * LSP4J client that manages one language server subprocess.
 *
 * <p>
 * The client starts the server command with stdio transport, sends the initialize
 * request, records the capabilities reported by the server and forwards document
 * notifications and requests only when the capability is supported. Startup, exit
 * handling and initialization timeout are coordinated through
 * {@link io.simpleide.thread.ThreadMaster}. Closing the client shuts down the server,
 * stops its executor and waits for the process to exit.
 *
 * <p>
 * Covered by {@code LspClientShutdownTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class LspClient implements LanguageClient, AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(LspClient.class);

	static {
		java.util.logging.Logger.getLogger("org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer")
			.setLevel(java.util.logging.Level.WARNING);
	}

	private final Consumer<PublishDiagnosticsParams> diagnosticsHandler;

	private final CompletableFuture<Void> initialized = new CompletableFuture<>();

	private final CompletableFuture<Void> shutdownComplete = new CompletableFuture<>();

	private final RunnableNode runtimeNode = new RunnableNode(() -> {
	});

	private final LspServerConnection connection;

	private final NodePattern startupNode;

	private volatile boolean closed;

	/**
	 * Creates a client without extra environment variables.
	 * @param command command and arguments used to start the language server
	 * @param workspaceRoot root directory passed to the server and used as its working
	 * directory
	 * @param diagnosticsHandler consumer for diagnostics published by the server
	 * @throws IOException when the client cannot register its startup work
	 */
	public LspClient(List<String> command, Path workspaceRoot, Consumer<PublishDiagnosticsParams> diagnosticsHandler)
			throws IOException {
		this(command, workspaceRoot, diagnosticsHandler, Map.of());
	}

	/**
	 * Creates a client and registers its startup work with the thread manager.
	 * @param command command and arguments used to start the language server
	 * @param workspaceRoot root directory passed to the server and used as its working
	 * directory
	 * @param diagnosticsHandler consumer for diagnostics published by the server, may be
	 * {@code null}
	 * @param environment extra environment variables for the server process
	 * @throws IOException when the client cannot register its startup work
	 */
	public LspClient(List<String> command, Path workspaceRoot, Consumer<PublishDiagnosticsParams> diagnosticsHandler,
			Map<String, String> environment) throws IOException {
		this(command, workspaceRoot, diagnosticsHandler, environment, null);
	}

	/**
	 * Creates a client whose startup and protocol tasks use a managed worker pool.
	 * @param command command and arguments used to start the language server
	 * @param workspaceRoot root directory passed to the server and used as its working
	 * directory
	 * @param diagnosticsHandler consumer for diagnostics published by the server, may be
	 * {@code null}
	 * @param environment extra environment variables for the server process
	 * @param workerPool managed pool for background language-server work
	 * @throws IOException when the client cannot register its startup work
	 */
	public LspClient(List<String> command, Path workspaceRoot, Consumer<PublishDiagnosticsParams> diagnosticsHandler,
			Map<String, String> environment, ManagedThreadPool workerPool) throws IOException {
		this(command, workspaceRoot, diagnosticsHandler, environment, workerPool, Map.of());
	}

	/**
	 * Creates a client with language-specific initialization options.
	 *
	 * <p>
	 * JDT LS uses the {@code bundles} option to load the Java debug adapter jar, which is
	 * downloaded at runtime by the IDE.
	 * @param command command and arguments used to start the language server
	 * @param workspaceRoot root directory passed to the server and used as its working
	 * directory
	 * @param diagnosticsHandler consumer for diagnostics published by the server, may be
	 * {@code null}
	 * @param environment extra environment variables for the server process
	 * @param workerPool managed pool for background language-server work
	 * @param initializationOptions options sent in the {@code initialize} request
	 * @throws IOException when the client cannot register its startup work
	 */
	public LspClient(List<String> command, Path workspaceRoot, Consumer<PublishDiagnosticsParams> diagnosticsHandler,
			Map<String, String> environment, ManagedThreadPool workerPool, Map<String, Object> initializationOptions)
			throws IOException {
		if (command == null || command.isEmpty()) {
			throw new IllegalArgumentException("Language server command must not be empty.");
		}
		if (environment == null) {
			throw new IllegalArgumentException("Language server environment must not be null.");
		}

		this.diagnosticsHandler = diagnosticsHandler == null ? ignored -> {
		} : diagnosticsHandler;
		this.connection = new LspServerConnection(this, initialized, shutdownComplete, workerPool, runtimeNode,
				initializationOptions == null ? Map.of() : Map.copyOf(initializationOptions), () -> closed,
				this::close);
		Runnable startup = () -> connection.start(command, workspaceRoot, environment);
		Runnable startupOnClose = () -> {
			if (!closed && !connection.isInitializationSubmitted()) {
				LOGGER.warn("Language server startup node closed before initialization was submitted.");
			}
		};
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(runtimeNode);
			startupNode = workerPool == null ? new RunnableNode(runtimeNode, startup, startupOnClose)
					: workerPool.execute(startup, startupOnClose);
			if (workerPool == null) {
				threadMaster.register(startupNode);
			}
		}
		catch (RuntimeException error) {
			threadMaster.removeNodeCascade(runtimeNode);
			throw error;
		}
	}

	/**
	 * Returns a future that completes when the server initialization finishes.
	 * @return the initialization future
	 */
	public CompletableFuture<Void> getInitialized() {
		return initialized;
	}

	/**
	 * Notifies the server that a document was opened.
	 * @param document document identifier, language id, version and text
	 */
	public void didOpen(TextDocumentItem document) {
		sendNotification("didOpen", () -> {
			LspCapabilities.ServerCapabilitiesSnapshot capabilities = connection.getCapabilities();
			if (capabilities.synchronizationKind() != TextDocumentSyncKind.None && capabilities.openCloseSupported()) {
				connection.getServer().getTextDocumentService().didOpen(new DidOpenTextDocumentParams(document));
			}
		});
	}

	/**
	 * Notifies the server that a document changed using the negotiated sync kind.
	 * @param uri document URI
	 * @param version new document version
	 * @param previousText text previously sent to the server
	 * @param text current document text
	 */
	public void didChange(String uri, int version, String previousText, String text) {
		sendNotification("didChange", () -> {
			TextDocumentSyncKind synchronizationKind = connection.getCapabilities().synchronizationKind();
			if (synchronizationKind == TextDocumentSyncKind.None || previousText.equals(text)) {
				return;
			}
			TextDocumentContentChangeEvent change = synchronizationKind == TextDocumentSyncKind.Incremental
					? new TextDocumentContentChangeEvent(
							new Range(new Position(0, 0), LspCapabilities.getEndPosition(previousText)), text)
					: new TextDocumentContentChangeEvent(text);
			connection.getServer()
				.getTextDocumentService()
				.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, version),
						List.of(change)));
		});
	}

	/**
	 * Requests document formatting when the server supports it.
	 * @param params formatting options and document identifier
	 * @return a future with the formatting edits, or an empty list when unsupported
	 */
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		return initialized.thenCompose(ignored -> connection.getCapabilities().formattingSupported()
				? connection.getServer().getTextDocumentService().formatting(params)
				: CompletableFuture.completedFuture(List.of()));
	}

	/**
	 * Notifies the server that a document was closed.
	 * @param uri document URI
	 */
	public void didClose(String uri) {
		sendNotification("didClose", () -> {
			LspCapabilities.ServerCapabilitiesSnapshot capabilities = connection.getCapabilities();
			if (capabilities.synchronizationKind() != TextDocumentSyncKind.None && capabilities.openCloseSupported()) {
				connection.getServer()
					.getTextDocumentService()
					.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
			}
		});
	}

	/**
	 * Notifies the server that a document was saved.
	 * @param uri document URI
	 * @param text saved document text, sent only when the server requested it
	 */
	public void didSave(String uri, String text) {
		sendNotification("didSave", () -> {
			LspCapabilities.ServerCapabilitiesSnapshot capabilities = connection.getCapabilities();
			if (capabilities.saveSupported()) {
				connection.getServer()
					.getTextDocumentService()
					.didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri),
							capabilities.saveIncludeText() ? text : null));
			}
		});
	}

	private void sendNotification(String method, Runnable notification) {
		initialized.thenRun(notification).whenComplete((ignored, error) -> {
			if (error != null) {
				LOGGER.error("Unable to send LSP {} notification.", method, error);
			}
		});
	}

	/**
	 * Requests completion when the server supports it.
	 * @param params completion position and context
	 * @return a future with either a completion list or an array of items
	 */
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
		return initialized.thenCompose(ignored -> connection.getCapabilities().completionSupported()
				? connection.getServer().getTextDocumentService().completion(params)
				: CompletableFuture.completedFuture(Either.forLeft(List.of())));
	}

	/**
	 * Requests the definition at a position when the server supports it.
	 * @param params definition position and document identifier
	 * @return a future with definition locations or links
	 */
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
			DefinitionParams params) {
		return initialized.thenCompose(ignored -> connection.getCapabilities().definitionSupported()
				? connection.getServer().getTextDocumentService().definition(params)
				: CompletableFuture.completedFuture(Either.forLeft(List.of())));
	}

	/**
	 * Runs a workspace command on the language server.
	 *
	 * <p>
	 * This backs the Java debug bridge, which starts the DAP server hosted by the JDT
	 * language server through the {@code vscode.java.startDebugSession} command.
	 * @param command command identifier understood by the server
	 * @param arguments command arguments, may be {@code null}
	 * @return a future completed with the command result
	 */
	public CompletableFuture<Object> executeCommand(String command, List<Object> arguments) {
		ExecuteCommandParams params = new ExecuteCommandParams(command,
				arguments == null ? List.of() : List.copyOf(arguments));
		return initialized.thenCompose(ignored -> connection.getServer().getWorkspaceService().executeCommand(params));
	}

	/**
	 * Logs a telemetry event sent by the server.
	 * @param event telemetry payload
	 */
	@Override
	public void telemetryEvent(Object event) {
		LOGGER.debug("Language server telemetry: {}", event);
	}

	/**
	 * Forwards diagnostics to the handler supplied when the client was created.
	 * @param params diagnostics published by the server
	 */
	@Override
	public void publishDiagnostics(PublishDiagnosticsParams params) {
		diagnosticsHandler.accept(params);
	}

	/**
	 * Logs a message sent by the server.
	 * @param message message content and type
	 */
	@Override
	public void showMessage(MessageParams message) {
		LOGGER.info("[LSP] {}", message.getMessage());
	}

	/**
	 * Logs a server message request and answers without selecting an action.
	 * @param request requested message and actions
	 * @return a future completed with {@code null}
	 */
	@Override
	public CompletableFuture<MessageActionItem> showMessageRequest(org.eclipse.lsp4j.ShowMessageRequestParams request) {
		LOGGER.info("[LSP] {}", request.getMessage());
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * Acknowledges capabilities registered dynamically by the server.
	 * @param params registrations requested by the server
	 * @return a completed future
	 */
	@Override
	public CompletableFuture<Void> registerCapability(RegistrationParams params) {
		LOGGER.debug("[LSP] registerCapability {}", params);
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * Acknowledges capabilities unregistered dynamically by the server.
	 * @param params unregistrations requested by the server
	 * @return a completed future
	 */
	@Override
	public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
		LOGGER.debug("[LSP] unregisterCapability {}", params);
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * Logs a log message sent by the server.
	 * @param message message content and type
	 */
	@Override
	public void logMessage(MessageParams message) {
		LOGGER.debug("[LSP] {}", message.getMessage());
	}

	/**
	 * Consumes the non-standard JDT LS language status notification.
	 * @param status notification payload
	 */
	@JsonNotification("language/status")
	public void languageStatus(Object status) {
		LOGGER.debug("[LSP] language/status {}", status);
	}

	/**
	 * Closes the client, shuts down the server and releases its process resources.
	 */
	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		if (!initialized.isDone()) {
			initialized
				.completeExceptionally(new IOException("Language server client was closed before initialization."));
		}
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.removeNodeCascade(startupNode);
		}
		catch (RuntimeException error) {
			LOGGER.warn("Unable to unregister language server startup node.", error);
		}

		RunnableNode shutdownNode = new RunnableNode(connection::shutdown, () -> {
			if (connection.isProcessAlive()) {
				LOGGER.warn("Language server process is still running after its shutdown task.");
			}
		});
		try {
			threadMaster.register(shutdownNode);
		}
		catch (RuntimeException error) {
			LOGGER.warn("Unable to schedule language server shutdown; closing it directly.", error);
			connection.shutdown();
		}
	}

	boolean isProcessAlive() {
		return connection.isProcessAlive();
	}

	/**
	 * Waits for the shutdown task to finish within a timeout.
	 * @param timeout maximum time to wait
	 * @param unit unit of the timeout
	 * @return {@code true} when shutdown completed, {@code false} on timeout or interrupt
	 */
	public boolean awaitShutdown(long timeout, TimeUnit unit) {
		try {
			shutdownComplete.get(timeout, unit);
			return true;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return false;
		}
		catch (ExecutionException error) {
			return true;
		}
		catch (TimeoutException error) {
			return false;
		}
	}

}
