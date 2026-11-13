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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.SynchronizationCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

public final class LspClient implements LanguageClient, AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LspClient.class);
  private static final long INITIALIZATION_TIMEOUT_SECONDS = 30;

  static {
    java.util.logging.Logger.getLogger("org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer")
        .setLevel(java.util.logging.Level.WARNING);
  }

  private final Consumer<PublishDiagnosticsParams> diagnosticsHandler;
  private final CompletableFuture<Void> initialized = new CompletableFuture<>();
  private final CompletableFuture<Void> shutdownComplete = new CompletableFuture<>();
  private final RunnableNode runtimeNode = new RunnableNode(() -> {
  });
  private final RunnableNode startupNode;
  private volatile Thread errorReaderThread;
  private volatile ExecutorService lspExecutor;
  private volatile ScheduledFuture<?> initializationTimeoutTask;
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

    this.diagnosticsHandler = diagnosticsHandler == null ? ignored -> {
    } : diagnosticsHandler;
    this.startupNode = new RunnableNode(
        runtimeNode,
        () -> startServer(command, workspaceRoot, environment),
        () -> {
          if (!closed && !initializationSubmitted) {
            LOGGER.warn("Language server startup node closed before initialization was submitted.");
          }
        });
    ThreadMaster threadMaster = ThreadMaster.getInstance();
    try {
      threadMaster.register(runtimeNode);
      threadMaster.register(startupNode);
    } catch (RuntimeException error) {
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
      lspExecutor = createLspExecutor();
      Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
          this,
          startedProcess.getInputStream(),
          startedProcess.getOutputStream(),
          lspExecutor,
          Function.identity());
      server = launcher.getRemoteProxy();
      launcher.startListening();

      startedProcess.onExit().thenAccept(exited -> {
        if (closed) {
          return;
        }
        try {
          ThreadMaster.getInstance().register(new RunnableNode(runtimeNode, () -> {
            if (!closed && !initialized.isDone()) {
              IOException error = new IOException(
                  "Language server exited before initialization (code " + exited.exitValue() + ").");
              if (initialized.completeExceptionally(error)) {
                LOGGER.error("Language server exited before initialization.", error);
                close();
              }
            } else if (!closed) {
              LOGGER.warn("Language server process exited with code {}.", exited.exitValue());
              close();
            }
          }));
        } catch (RuntimeException error) {
          LOGGER.error("Unable to schedule language server exit handling.", error);
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
              new Range(
                  new Position(0, 0),
                  getEndPosition(previousText)),
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
      params.setWorkspaceFolders(List.of(
          new WorkspaceFolder(
              workspaceRoot.toUri().toString(),
              workspaceRoot.getFileName().toString())));
    }

    initializationTimeoutTask = ThreadMaster.getInstance().schedule(
        runtimeNode,
        () -> {
          TimeoutException error = new TimeoutException("Language server initialization timed out.");
          if (initialized.completeExceptionally(error)) {
            LOGGER.error("Unable to initialize language server.", error);
            close();
          }
        },
        INITIALIZATION_TIMEOUT_SECONDS,
        TimeUnit.SECONDS);
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
    return sync.isLeft()
        || sync.getRight() != null && Boolean.TRUE.equals(sync.getRight().getOpenClose());
  }

  private boolean hasSaveSupport(org.eclipse.lsp4j.InitializeResult result) {
    Either<TextDocumentSyncKind, org.eclipse.lsp4j.TextDocumentSyncOptions> sync = result.getCapabilities()
        .getTextDocumentSync();
    if (sync == null || sync.isLeft() || sync.getRight() == null) {
      return false;
    }

    Either<Boolean, SaveOptions> save = sync.getRight().getSave();
    return save != null && (save.isLeft()
        ? Boolean.TRUE.equals(save.getLeft())
        : save.getRight() != null);
  }

  private boolean hasFormattingSupport(org.eclipse.lsp4j.InitializeResult result) {
    Either<Boolean, org.eclipse.lsp4j.DocumentFormattingOptions> provider = result.getCapabilities()
        .getDocumentFormattingProvider();
    return provider != null && (provider.isLeft()
        ? Boolean.TRUE.equals(provider.getLeft())
        : provider.getRight() != null);
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
    return provider != null && (provider.isLeft()
        ? Boolean.TRUE.equals(provider.getLeft())
        : provider.getRight() != null);
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
    }, "simpleide-lsp-stderr-" + serverProcess.pid());
    errorReader.setDaemon(true);
    errorReaderThread = errorReader;
    errorReader.start();
  }

  private ExecutorService createLspExecutor() {
    AtomicInteger threadNumber = new AtomicInteger();
    String threadPrefix = "simpleide-lsp-" + runtimeNode.getId() + "-";
    return Executors.newFixedThreadPool(2, task -> {
      Thread thread = new Thread(task, threadPrefix + threadNumber.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    });
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

  boolean isProcessAlive() {
    Process child = process;
    return child != null && child.isAlive();
  }

  public boolean awaitShutdown(long timeout, TimeUnit unit) {
    try {
      shutdownComplete.get(timeout, unit);
      return true;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ExecutionException error) {
      return true;
    } catch (TimeoutException error) {
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
        } catch (InterruptedException error) {
          interrupted = true;
        } catch (ExecutionException | TimeoutException | RuntimeException ignored) {
        }
        try {
          activeServer.exit();
        } catch (RuntimeException ignored) {
        }
      }
    } finally {
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
        } catch (IOException error) {
          LOGGER.debug("Unable to close language server stderr.", error);
        }
      }
    } finally {
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
      boolean wasInterrupted = Thread.interrupted();
      Thread errorReader = errorReaderThread;
      errorReaderThread = null;
      if (errorReader != null && errorReader != Thread.currentThread()) {
        errorReader.interrupt();
        try {
          errorReader.join(250);
        } catch (InterruptedException error) {
          wasInterrupted = true;
        }
        if (errorReader.isAlive()) {
          LOGGER.warn("Language server stderr reader did not stop after process shutdown.");
        }
      }
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
      try {
        ThreadMaster.getInstance().removeNodeCascade(runtimeNode);
      } finally {
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
        } catch (InterruptedException error) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
