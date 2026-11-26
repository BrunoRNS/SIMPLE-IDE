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
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DefinitionOptions;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.SimpleIDEVersion;
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
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public final class LspClient implements LanguageClient, AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(LspClient.class);

	/**
	 * Servers that build an index of the workspace during initialization, such as
	 * kotlin-language-server and groovy-language-server, need longer than a minute on a
	 * cold cache before they answer the {@code initialize} request.
	 */
	private static final long INITIALIZATION_TIMEOUT_SECONDS = 180;

	private static final int MAX_RECENT_ERROR_LINES = 20;

	static {
		java.util.logging.Logger.getLogger("org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer")
			.setLevel(java.util.logging.Level.WARNING);
	}

	private final Consumer<PublishDiagnosticsParams> diagnosticsHandler;

	private final Map<String, Object> initializationOptions;

	private final CompletableFuture<Void> initialized = new CompletableFuture<>();

	private final CompletableFuture<Void> shutdownComplete = new CompletableFuture<>();

	private final Deque<String> recentErrorLines = new ConcurrentLinkedDeque<>();

	private final RunnableNode runtimeNode = new RunnableNode(() -> {
	});

	private final ManagedThreadPool workerPool;

	private final NodePattern startupNode;

	private volatile ManagedThreadPool errorReaderPool;

	private volatile ExecutorService lspExecutor;

	private volatile ScheduledFuture<?> initializationTimeoutTask;

	private volatile Process process;

	private volatile LanguageServer server;

	private volatile boolean initializationSubmitted;

	private volatile boolean closed;

	private volatile ServerCapabilitiesSnapshot serverCapabilities = ServerCapabilitiesSnapshot.NONE;

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
		this.initializationOptions = initializationOptions == null ? Map.of() : Map.copyOf(initializationOptions);
		this.workerPool = workerPool;
		Runnable startup = () -> startServer(command, workspaceRoot, environment);
		Runnable startupOnClose = () -> {
			if (!closed && !initializationSubmitted) {
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

	private void startServer(List<String> command, Path workspaceRoot, Map<String, String> environment) {
		if (closed) {
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
			if (closed) {
				closeProcess();
				return;
			}

			startErrorStreamReader(startedProcess);
			Launcher<LanguageServer> launcher;
			boolean aborted;
			synchronized (this) {
				aborted = closed;
				if (!aborted) {
					lspExecutor = createLspExecutor();
					launcher = LSPLauncher.createClientLauncher(this, startedProcess.getInputStream(),
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
				if (closed) {
					return;
				}
				try {
					ThreadMaster.getInstance().register(new RunnableNode(runtimeNode, () -> {
						if (!closed && !initialized.isDone()) {
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
								close();
							}
						}
						else if (!closed) {
							LOGGER.warn("Language server process exited with code {}.", exited.exitValue());
							close();
						}
					}));
				}
				catch (RuntimeException error) {
					LOGGER.error("Unable to schedule language server exit handling.", error);
				}
			});

			if (closed) {
				closeProcess();
				return;
			}
			initializationSubmitted = true;
			initialize(workspaceRoot);
		}
		catch (IOException | RuntimeException error) {
			if (closed) {
				LOGGER.debug("Language server startup stopped after the client was closed.", error);
				closeProcess();
				return;
			}
			initialized.completeExceptionally(error);
			LOGGER.error("Unable to start language server.", error);
			close();
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
			ServerCapabilitiesSnapshot capabilities = serverCapabilities;
			if (capabilities.synchronizationKind() != TextDocumentSyncKind.None && capabilities.openCloseSupported()) {
				server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(document));
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
			TextDocumentSyncKind synchronizationKind = serverCapabilities.synchronizationKind();
			if (synchronizationKind == TextDocumentSyncKind.None || previousText.equals(text)) {
				return;
			}
			TextDocumentContentChangeEvent change = synchronizationKind == TextDocumentSyncKind.Incremental
					? new TextDocumentContentChangeEvent(new Range(new Position(0, 0), getEndPosition(previousText)),
							text)
					: new TextDocumentContentChangeEvent(text);
			server.getTextDocumentService()
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
		return initialized.thenCompose(ignored -> serverCapabilities.formattingSupported()
				? server.getTextDocumentService().formatting(params) : CompletableFuture.completedFuture(List.of()));
	}

	/**
	 * Notifies the server that a document was closed.
	 * @param uri document URI
	 */
	public void didClose(String uri) {
		sendNotification("didClose", () -> {
			ServerCapabilitiesSnapshot capabilities = serverCapabilities;
			if (capabilities.synchronizationKind() != TextDocumentSyncKind.None && capabilities.openCloseSupported()) {
				server.getTextDocumentService()
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
			ServerCapabilitiesSnapshot capabilities = serverCapabilities;
			if (capabilities.saveSupported()) {
				server.getTextDocumentService()
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
		return initialized.thenCompose(
				ignored -> serverCapabilities.completionSupported() ? server.getTextDocumentService().completion(params)
						: CompletableFuture.completedFuture(Either.forLeft(List.of())));
	}

	/**
	 * Requests the definition at a position when the server supports it.
	 * @param params definition position and document identifier
	 * @return a future with definition locations or links
	 */
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
			DefinitionParams params) {
		return initialized.thenCompose(
				ignored -> serverCapabilities.definitionSupported() ? server.getTextDocumentService().definition(params)
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
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public CompletableFuture<Object> executeCommand(String command, List<Object> arguments) {
		ExecuteCommandParams params = new ExecuteCommandParams(command,
				arguments == null ? List.of() : List.copyOf(arguments));
		return initialized.thenCompose(ignored -> server.getWorkspaceService().executeCommand(params));
	}

	@SuppressWarnings("deprecation")
	private void initialize(Path workspaceRoot) {
		InitializeParams params = new InitializeParams();

		params.setProcessId(Math.toIntExact(ProcessHandle.current().pid()));
		params.setClientInfo(new ClientInfo("SIMPLE-IDE", SimpleIDEVersion.VERSION));
		params.setCapabilities(createClientCapabilities());
		if (!initializationOptions.isEmpty()) {
			params.setInitializationOptions(initializationOptions);
		}

		if (workspaceRoot != null) {
			String rootUri = workspaceRoot.toUri().toString();
			params.setRootUri(rootUri);
			params.setRootPath(workspaceRoot.toAbsolutePath().toString());
			params.setWorkspaceFolders(List.of(new WorkspaceFolder(rootUri, workspaceRoot.getFileName().toString())));
		}

		initializationTimeoutTask = ThreadMaster.getInstance().schedule(runtimeNode, () -> {
			TimeoutException error = new TimeoutException("Language server initialization timed out.");
			if (initialized.completeExceptionally(error)) {
				LOGGER.error("Unable to initialize language server.", error);
				close();
			}
		}, INITIALIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		server.initialize(params).whenComplete((result, error) -> {
			ScheduledFuture<?> timeoutTask = initializationTimeoutTask;
			if (timeoutTask != null) {
				timeoutTask.cancel(false);
			}
			if (closed || initialized.isDone()) {
				return;
			}
			if (error != null) {
				initialized.completeExceptionally(error);
				LOGGER.error("Unable to initialize language server.", error);
				close();
				return;
			}
			try {
				if (result == null || result.getCapabilities() == null) {
					throw new IllegalStateException("Language server returned no capabilities.");
				}
				serverCapabilities = new ServerCapabilitiesSnapshot(getSynchronizationKind(result),
						hasOpenCloseSupport(result), result.getCapabilities().getCompletionProvider() != null,
						hasDefinitionSupport(result), hasSaveSupport(result), shouldIncludeTextOnSave(result),
						hasFormattingSupport(result));
				server.initialized(new InitializedParams());
				initialized.complete(null);
				LOGGER.info("Language server initialized for workspace {}.",
						workspaceRoot == null ? "(none)" : workspaceRoot);
			}
			catch (RuntimeException initializationError) {
				initialized.completeExceptionally(initializationError);
				LOGGER.error("Unable to finish language server initialization.", initializationError);
				close();
			}
		});
	}

	private TextDocumentSyncKind getSynchronizationKind(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null) {
			return TextDocumentSyncKind.None;
		}
		if (sync.isLeft()) {
			return sync.getLeft();
		}
		if (sync.getRight() == null) {
			return TextDocumentSyncKind.None;
		}
		TextDocumentSyncKind change = sync.getRight().getChange();
		return change == null ? TextDocumentSyncKind.None : change;
	}

	private boolean hasOpenCloseSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null) {
			return false;
		}
		return sync.isLeft() || sync.getRight() != null && Boolean.TRUE.equals(sync.getRight().getOpenClose());
	}

	private boolean hasSaveSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null || sync.isLeft() || sync.getRight() == null) {
			return false;
		}

		Either<Boolean, SaveOptions> save = sync.getRight().getSave();
		return save != null && (save.isLeft() ? Boolean.TRUE.equals(save.getLeft()) : save.getRight() != null);
	}

	private boolean hasFormattingSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<Boolean, org.eclipse.lsp4j.DocumentFormattingOptions> provider = result.getCapabilities()
			.getDocumentFormattingProvider();
		return provider != null
				&& (provider.isLeft() ? Boolean.TRUE.equals(provider.getLeft()) : provider.getRight() != null);
	}

	private boolean shouldIncludeTextOnSave(org.eclipse.lsp4j.InitializeResult result) {
		Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
			.getTextDocumentSync();
		if (sync == null || sync.isLeft() || sync.getRight() == null) {
			return false;
		}

		Either<Boolean, SaveOptions> save = sync.getRight().getSave();
		return save != null && save.isRight() && save.getRight() != null
				&& Boolean.TRUE.equals(save.getRight().getIncludeText());
	}

	private boolean hasDefinitionSupport(org.eclipse.lsp4j.InitializeResult result) {
		Either<Boolean, DefinitionOptions> provider = result.getCapabilities().getDefinitionProvider();
		return provider != null
				&& (provider.isLeft() ? Boolean.TRUE.equals(provider.getLeft()) : provider.getRight() != null);
	}

	private record ServerCapabilitiesSnapshot(TextDocumentSyncKind synchronizationKind, boolean openCloseSupported,
			boolean completionSupported, boolean definitionSupported, boolean saveSupported, boolean saveIncludeText,
			boolean formattingSupported) {

		private static final ServerCapabilitiesSnapshot NONE = new ServerCapabilitiesSnapshot(TextDocumentSyncKind.None,
				false, false, false, false, false, false);

	}

	private Position getEndPosition(String text) {
		int line = 0;
		int character = 0;
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			switch (current) {
				case '\n' -> {
					line++;
					character = 0;
				}
				case '\r' -> {
					if (index + 1 == text.length() || text.charAt(index + 1) != '\n') {
						line++;
						character = 0;
					}
				}
				default -> character++;
			}
		}
		return new Position(line, character);
	}

	private ClientCapabilities createClientCapabilities() {
		CompletionCapabilities completionCapabilities = new CompletionCapabilities();
		completionCapabilities.setContextSupport(true);
		CompletionItemCapabilities completionItemCapabilities = new CompletionItemCapabilities();
		completionItemCapabilities.setSnippetSupport(false);
		completionCapabilities.setCompletionItem(completionItemCapabilities);

		TextDocumentClientCapabilities textDocumentCapabilities = new TextDocumentClientCapabilities();
		textDocumentCapabilities.setCompletion(completionCapabilities);

		DefinitionCapabilities definitionCapabilities = new DefinitionCapabilities();
		definitionCapabilities.setLinkSupport(true);
		textDocumentCapabilities.setDefinition(definitionCapabilities);

		FormattingCapabilities formattingCapabilities = new FormattingCapabilities();
		formattingCapabilities.setDynamicRegistration(false);
		textDocumentCapabilities.setFormatting(formattingCapabilities);

		SynchronizationCapabilities synchronizationCapabilities = new SynchronizationCapabilities();
		synchronizationCapabilities.setDidSave(true);
		textDocumentCapabilities.setSynchronization(synchronizationCapabilities);
		textDocumentCapabilities.setPublishDiagnostics(new PublishDiagnosticsCapabilities(true));

		ClientCapabilities capabilities = new ClientCapabilities();
		capabilities.setTextDocument(textDocumentCapabilities);

		WorkspaceClientCapabilities workspaceCapabilities = new WorkspaceClientCapabilities();
		workspaceCapabilities.setWorkspaceFolders(true);
		capabilities.setWorkspace(workspaceCapabilities);
		return capabilities;
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
					if (!closed) {
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

		RunnableNode shutdownNode = new RunnableNode(this::shutdownServer, () -> {
			Process child = process;
			if (child != null && child.isAlive()) {
				LOGGER.warn("Language server process is still running after its shutdown task.");
			}
		});
		try {
			threadMaster.register(shutdownNode);
		}
		catch (RuntimeException error) {
			LOGGER.warn("Unable to schedule language server shutdown; closing it directly.", error);
			shutdownServer();
		}
	}

	boolean isProcessAlive() {
		Process child = process;
		return child != null && child.isAlive();
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

	private void shutdownServer() {
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
