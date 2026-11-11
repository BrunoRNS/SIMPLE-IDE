package io.simpleide.lsp.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.lsp4j.FormattingCapabilities;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ClientInfo;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DefinitionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

public final class LspClient implements LanguageClient, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LspClient.class);
  private static final long INITIALIZATION_TIMEOUT_SECONDS = 30;

  private final Consumer<PublishDiagnosticsParams> diagnosticsHandler;
  private final CompletableFuture<Void> initialized = new CompletableFuture<>();
  private final RunnableNode startupNode;
  private volatile Process process;
  private volatile LanguageServer server;
  private volatile boolean initializationSubmitted;
  private volatile boolean closed;
  private volatile TextDocumentSyncKind synchronizationKind = TextDocumentSyncKind.None;
  private volatile boolean openCloseSupported;
  private volatile boolean completionSupported;
  private volatile boolean definitionSupported;
  private volatile boolean saveSupported;
  private volatile boolean saveIncludeText;
  private volatile boolean formattingSupported;

  public LspClient(List<String> command, Path workspaceRoot, Consumer<PublishDiagnosticsParams> diagnosticsHandler)
      throws IOException {
    this(command, workspaceRoot, diagnosticsHandler, Map.of());
  }

  public LspClient(
      List<String> command,
      Path workspaceRoot,
      Consumer<PublishDiagnosticsParams> diagnosticsHandler,
      Map<String, String> environment)
      throws IOException {
    if (command == null || command.isEmpty()) {
      throw new IllegalArgumentException("Language server command must not be empty.");
    }
    if (environment == null) {
      throw new IllegalArgumentException("Language server environment must not be null.");
    }

    this.diagnosticsHandler = diagnosticsHandler == null ? ignored -> { } : diagnosticsHandler;
    this.startupNode = new RunnableNode(
        () -> startServer(command, workspaceRoot, environment),
        () -> {
          if (!closed && !initializationSubmitted) {
            LOGGER.warn("Language server startup node closed before initialization was submitted.");
          }
        });
    ThreadMaster threadMaster = ThreadMaster.getInstance();
    try {
      threadMaster.register(startupNode);
    } catch (RuntimeException error) {
      threadMaster.removeNodeCascade(startupNode);
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
      Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
          this, startedProcess.getInputStream(), startedProcess.getOutputStream());
      server = launcher.getRemoteProxy();
      launcher.startListening();

      startedProcess.onExit().thenAccept(exited -> {
        if (!closed && !initialized.isDone()) {
          IOException error = new IOException(
              "Language server exited before initialization (code " + exited.exitValue() + ").");
          if (initialized.completeExceptionally(error)) {
            LOGGER.error("Language server exited before initialization.", error);
            close();
          }
        } else if (!closed) {
          LOGGER.warn("Language server process exited with code {}.", exited.exitValue());
        }
      });

      if (closed) {
        closeProcess();
        return;
      }
      initializationSubmitted = true;
      initialize(workspaceRoot);
    } catch (IOException | RuntimeException error) {
      initialized.completeExceptionally(error);
      LOGGER.error("Unable to start language server.", error);
      close();
    }
  }

  public CompletableFuture<Void> getInitialized() {
    return initialized;
  }

  public void didOpen(TextDocumentItem document) {
    sendNotification("didOpen", () -> {
      if (synchronizationKind != TextDocumentSyncKind.None && openCloseSupported) {
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(document));
      }
    });
  }

  public void didChange(String uri, int version, String previousText, String text) {
    sendNotification("didChange", () -> {
      if (synchronizationKind == TextDocumentSyncKind.None || previousText.equals(text)) {
        return;
      }
      TextDocumentContentChangeEvent change = synchronizationKind == TextDocumentSyncKind.Incremental
          ? new TextDocumentContentChangeEvent(
              new Range(new Position(0, 0), getEndPosition(previousText)),
              previousText.length(),
              text)
          : new TextDocumentContentChangeEvent(text);
      server.getTextDocumentService().didChange(
          new DidChangeTextDocumentParams(
              new VersionedTextDocumentIdentifier(uri, version),
              List.of(change)));
    });
  }

  public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    return initialized.thenCompose(ignored -> formattingSupported
        ? server.getTextDocumentService().formatting(params)
        : CompletableFuture.completedFuture(List.of()));
  }

  public void didClose(String uri) {
    sendNotification("didClose", () -> {
      if (synchronizationKind != TextDocumentSyncKind.None && openCloseSupported) {
        server.getTextDocumentService().didClose(
            new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
      }
    });
  }

  public void didSave(String uri, String text) {
    sendNotification("didSave", () -> {
      if (saveSupported) {
        server.getTextDocumentService().didSave(
            new DidSaveTextDocumentParams(
                new TextDocumentIdentifier(uri),
                saveIncludeText ? text : null));
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

  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
    return initialized.thenCompose(ignored -> completionSupported
        ? server.getTextDocumentService().completion(params)
        : CompletableFuture.completedFuture(Either.forLeft(List.of())));
  }

  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
      DefinitionParams params) {
    return initialized.thenCompose(ignored -> definitionSupported
        ? server.getTextDocumentService().definition(params)
        : CompletableFuture.completedFuture(Either.forLeft(List.of())));
  }

  private void initialize(Path workspaceRoot) {
    InitializeParams params = new InitializeParams();
    
    params.setProcessId(Math.toIntExact(ProcessHandle.current().pid()));
    params.setClientInfo(new ClientInfo("SIMPLE-IDE", "0.0.1"));
    params.setCapabilities(createClientCapabilities());
    if (workspaceRoot != null) {
      params.setRootPath(workspaceRoot.toString());
      params.setRootUri(workspaceRoot.toUri().toString());
    }

    server.initialize(params)
        .orTimeout(INITIALIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .whenComplete((result, error) -> {
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
            synchronizationKind = getSynchronizationKind(result);
            openCloseSupported = hasOpenCloseSupport(result);
            completionSupported = result.getCapabilities().getCompletionProvider() != null;
            definitionSupported = hasDefinitionSupport(result);
            saveSupported = hasSaveSupport(result);
            formattingSupported = hasFormattingSupport(result);
            saveIncludeText = shouldIncludeTextOnSave(result);
            server.initialized(new InitializedParams());
            initialized.complete(null);
            LOGGER.info("Language server initialized for workspace {}.",
                workspaceRoot == null ? "(none)" : workspaceRoot);
          } catch (RuntimeException initializationError) {
            initialized.completeExceptionally(initializationError);
            LOGGER.error("Unable to finish language server initialization.", initializationError);
            close();
          }
        });
  }

  private TextDocumentSyncKind getSynchronizationKind(org.eclipse.lsp4j.InitializeResult result) {
    Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync =
        result.getCapabilities().getTextDocumentSync();
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
    Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync =
        result.getCapabilities().getTextDocumentSync();
    if (sync == null) {
      return false;
    }
    return sync.isLeft()
        || sync.getRight() != null && Boolean.TRUE.equals(sync.getRight().getOpenClose());
  }

  private boolean hasSaveSupport(org.eclipse.lsp4j.InitializeResult result) {
    Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync =
        result.getCapabilities().getTextDocumentSync();
    if (sync == null || sync.isLeft() || sync.getRight() == null) {
      return false;
    }

    Either<Boolean, SaveOptions> save = sync.getRight().getSave();
    return save != null && (save.isLeft()
        ? Boolean.TRUE.equals(save.getLeft())
        : save.getRight() != null);
  }
	private boolean hasFormattingSupport(org.eclipse.lsp4j.InitializeResult result) {
    Either<Boolean, org.eclipse.lsp4j.DocumentFormattingOptions> provider =
        result.getCapabilities().getDocumentFormattingProvider();
    return provider != null && (provider.isLeft()
        ? Boolean.TRUE.equals(provider.getLeft())
        : provider.getRight() != null);
  }
  private boolean shouldIncludeTextOnSave(org.eclipse.lsp4j.InitializeResult result) {
    Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync =
        result.getCapabilities().getTextDocumentSync();
    if (sync == null || sync.isLeft() || sync.getRight() == null) {
      return false;
    }

    Either<Boolean, SaveOptions> save = sync.getRight().getSave();
    return save != null && save.isRight() && save.getRight() != null
        && Boolean.TRUE.equals(save.getRight().getIncludeText());
  }

  private boolean hasDefinitionSupport(org.eclipse.lsp4j.InitializeResult result) {
    Either<Boolean, DefinitionOptions> provider = result.getCapabilities().getDefinitionProvider();
    return provider != null && (provider.isLeft()
        ? Boolean.TRUE.equals(provider.getLeft())
        : provider.getRight() != null);
  }

  private Position getEndPosition(String text) {
    int line = 0;
    int character = 0;
    for (int index = 0; index < text.length(); index++) {
      char current = text.charAt(index);
      if (current == '\n') {
        line++;
        character = 0;
      } else if (current == '\r') {
        if (index + 1 == text.length() || text.charAt(index + 1) != '\n') {
          line++;
          character = 0;
        }
      } else {
        character++;
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
    return capabilities;
  }
  private void startErrorStreamReader(Process serverProcess) {
    Thread errorReader = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(serverProcess.getErrorStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          LOGGER.debug("[LSP server] {}", line);
        }
      } catch (IOException error) {
        if (!closed) {
          LOGGER.warn("Unable to read language server output.", error);
        }
      }
    }, "simpleide-lsp-stderr");
    errorReader.setDaemon(true);
    errorReader.start();
  }

  @Override
  public void telemetryEvent(Object event) {
    LOGGER.debug("Language server telemetry: {}", event);
  }

  @Override
  public void publishDiagnostics(PublishDiagnosticsParams params) {
    diagnosticsHandler.accept(params);
  }

  @Override
  public void showMessage(MessageParams message) {
    LOGGER.info("[LSP] {}", message.getMessage());
  }

  @Override
  public CompletableFuture<MessageActionItem> showMessageRequest(
      org.eclipse.lsp4j.ShowMessageRequestParams request) {
    LOGGER.info("[LSP] {}", request.getMessage());
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void logMessage(MessageParams message) {
    LOGGER.debug("[LSP] {}", message.getMessage());
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (!initialized.isDone()) {
      initialized.completeExceptionally(new IOException("Language server client was closed before initialization."));
    }
    ThreadMaster threadMaster = ThreadMaster.getInstance();
    try {
      threadMaster.removeNodeCascade(startupNode);
    } catch (RuntimeException error) {
      LOGGER.warn("Unable to unregister language server startup node.", error);
    }

    RunnableNode shutdownNode = new RunnableNode(
        this::shutdownServer,
        () -> {
          Process child = process;
          if (child != null && child.isAlive()) {
            LOGGER.warn("Language server process is still running after its shutdown task.");
          }
        });
    try {
      threadMaster.register(shutdownNode);
    } catch (RuntimeException error) {
      LOGGER.warn("Unable to schedule language server shutdown; closing it directly.", error);
      shutdownServer();
    }
  }

  private void shutdownServer() {
    LanguageServer activeServer = server;
    if (initialized.isDone() && !initialized.isCompletedExceptionally() && activeServer != null) {
      try {
        activeServer.shutdown().get(2, TimeUnit.SECONDS);
        activeServer.exit();
      } catch (Exception error) {
        LOGGER.debug("Language server shutdown did not complete cleanly.", error);
      }
    }
    closeProcess();
  }

  private void closeProcess() {
    Process child = process;
    if (child == null) {
      return;
    }
    child.destroy();
    try {
      if (!child.waitFor(2, TimeUnit.SECONDS)) {
        child.destroyForcibly();
        if (!child.waitFor(2, TimeUnit.SECONDS)) {
          LOGGER.warn("Unable to terminate language server process.");
        }
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      child.destroyForcibly();
      LOGGER.warn("Interrupted while closing language server process.", error);
    }
  }
}
