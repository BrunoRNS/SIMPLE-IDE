package io.simpleide.lsp.core;

import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.client.LspServerStartupException;
import io.simpleide.lsp.dowload.LspManager;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
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
import java.util.function.Consumer;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LanguageServerManager implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerManager.class);

  private static final Set<String> WORKSPACE_MARKERS =
      Set.of(
          "pom.xml",
          "build.gradle",
          "build.gradle.kts",
          "settings.gradle",
          "settings.gradle.kts",
          "Cargo.toml",
          "package.json",
          "pyproject.toml",
          "setup.py",
          "go.mod",
          "compile_commands.json");

  private final Map<ServerKey, ServerEntry> servers = new ConcurrentHashMap<>();
  private final Consumer<LspStatus> statusListener;
  private volatile boolean closed;

  public LanguageServerManager() {
    this(ignored -> {});
  }

  public LanguageServerManager(Consumer<LspStatus> statusListener) {
    this.statusListener = Objects.requireNonNull(statusListener, "statusListener");
  }

  public CompletableFuture<LspClient> connect(
      Document document, Consumer<PublishDiagnosticsParams> diagnosticsHandler) {

    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(diagnosticsHandler, "diagnosticsHandler");
    if (closed) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Language server manager is closed."));
    }

    Path root = findWorkspaceRoot(document.getPath());
    Map<String, String> environment;

    try {
      environment = getEnvironment(document.getLanguageId());
    } catch (IllegalArgumentException | IllegalStateException error) {
      return CompletableFuture.failedFuture(error);
    }

    ServerKey key = new ServerKey(document.getLanguageId(), root);
    String statusId = statusId(key);
    ServerEntry entry;
    try {
      entry =
          servers.computeIfAbsent(
              key,
              ignored -> new ServerEntry(document.getLanguageId(), root, environment, statusId));
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
    if (closed) {
      servers.remove(key, entry);
      entry.client.whenComplete(
          (client, error) -> {
            if (client != null) {
              client.close();
            }
          });
      ThreadMaster.getInstance().removeNodeCascade(entry.startupNode);
      return CompletableFuture.failedFuture(
          new IllegalStateException("Language server manager is closed."));
    }
    entry.diagnosticsHandlers.put(document.getUri(), diagnosticsHandler);
    return entry.client.whenComplete(
        (client, error) -> {
          if (error != null || client == null) {
            servers.remove(key, entry);
          }
        });
  }

  public void disconnect(Document document) {
    for (ServerEntry entry : servers.values()) {
      entry.diagnosticsHandlers.remove(document.getUri());
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    List<LspClient> clients = new ArrayList<>();
    List<ServerEntry> entries = new ArrayList<>(servers.values());
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    for (ServerEntry entry : entries) {
      try {
        ThreadMaster.getInstance().removeNodeCascade(entry.startupNode);
      } catch (RuntimeException error) {
        LOGGER.warn("Unable to unregister language server startup task.", error);
      }
      if (!entry.client.isDone()) {
        entry.client.completeExceptionally(
            new CancellationException("Language server manager is closed."));
      }
      LspClient client = awaitClient(entry.client, deadline);
      if (client == null) {
        client = entry.activeClient();
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
  }

  private LspClient awaitClient(CompletableFuture<LspClient> client, long deadline) {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) {
      return completedClient(client);
    }
    try {
      return client.get(remaining, TimeUnit.NANOSECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return completedClient(client);
    } catch (ExecutionException | TimeoutException error) {
      return completedClient(client);
    }
  }

  private LspClient completedClient(CompletableFuture<LspClient> client) {
    if (!client.isDone() || client.isCompletedExceptionally() || client.isCancelled()) {
      return null;
    }
    try {
      return client.getNow(null);
    } catch (RuntimeException error) {
      return null;
    }
  }

  private String statusId(ServerKey key) {
    return key.languageId() + "@" + key.workspaceRoot();
  }

  private void publishStatus(String id, String serverName, LspStatus.State state) {
    statusListener.accept(new LspStatus(id, serverName, state));
  }

  private Map<String, String> getEnvironment(String languageId) {
    if (!"java".equals(languageId)) {
      return Map.of();
    }

    String javaHome = System.getProperty("simpleide.lsp.javaHome");
    if (javaHome == null || javaHome.isBlank()) {
      return Map.of();
    }

    Path path = Path.of(javaHome).toAbsolutePath().normalize();
    if (!Files.isDirectory(path)) {
      throw new IllegalArgumentException("Configured LSP Java home is not a directory: " + path);
    }
    return Map.of("JAVA_HOME", path.toString());
  }

  private Path findWorkspaceRoot(Path documentPath) {
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

  private final class ServerEntry {
    private final Map<String, Consumer<PublishDiagnosticsParams>> diagnosticsHandlers =
        new ConcurrentHashMap<>();
    private final CompletableFuture<LspClient> client = new CompletableFuture<>();
    private final String languageId;
    private final Path root;
    private final Map<String, String> environment;
    private final String statusId;
    private final RunnableNode startupNode;
    private volatile LspClient activeClient;
    private volatile boolean startupFinished;

    private ServerEntry(
        String languageId, Path root, Map<String, String> environment, String statusId) {
      this.languageId = languageId;
      this.root = root;
      this.environment = environment;
      this.statusId = statusId;
      startupNode = new RunnableNode(this::start, this::onStartupNodeClosed);
      client.whenComplete(
          (lspClient, error) -> {
            if (error != null) {
              if (!closed) {
                LOGGER.error(
                    "Unable to start language server {} for workspace {}.",
                    languageId,
                    root,
                    error);
                publishStatus(statusId, languageId, LspStatus.State.FAILED);
              }
            } else if (closed && lspClient != null) {
              lspClient.close();
            }
          });

      try {
        ThreadMaster.getInstance().register(startupNode);
      } catch (RuntimeException error) {
        ThreadMaster.getInstance().removeNodeCascade(startupNode);
        client.completeExceptionally(error);
      }
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
      } catch (RuntimeException error) {
        client.completeExceptionally(error);
      } finally {
        startupFinished = true;
      }
    }

    private void startAttempt(List<String> command, int attempt) {
      if (closed) {
        closeActiveClient();
        client.completeExceptionally(
            new CancellationException("Language server startup was cancelled."));
        return;
      }

      publishStatus(statusId, languageId, LspStatus.State.STARTING);
      LspClient lspClient;
      try {
        lspClient =
            new LspClient(
                command,
                root,
                diagnostics -> {
                  Consumer<PublishDiagnosticsParams> handler =
                      diagnosticsHandlers.get(diagnostics.getUri());
                  if (handler != null) {
                    handler.accept(diagnostics);
                  }
                },
                environment);
      } catch (IOException | RuntimeException error) {
        handleStartupFailure(error, attempt);
        return;
      }

      activeClient = lspClient;
      if (closed) {
        closeActiveClient();
        client.completeExceptionally(
            new CancellationException("Language server startup was cancelled."));
        return;
      }

      lspClient
          .getInitialized()
          .whenComplete(
              (ignored, error) -> {
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
                } else {
                  handleStartupFailure(unwrapCompletionException(error), attempt);
                }
              });
    }

    private void handleStartupFailure(Throwable error, int attempt) {
      if (closed) {
        closeActiveClient();
        return;
      }

      boolean jvmCompatibilityFailure =
          error instanceof LspServerStartupException startup && startup.isJvmCompatibilityFailure();
      if (jvmCompatibilityFailure) {
        if (attempt == 0) {
          List<String> fallbackCommand = new ArrayList<>();
          try {
            fallbackCommand =
                LspManager.getFallbackCommandRun(languageId, root, statusId, statusListener);
          } catch (RuntimeException fallbackError) {
            error.addSuppressed(fallbackError);
          }
          if (closed) {
            closeActiveClient();
            return;
          }
          if (!fallbackCommand.isEmpty()) {
            LOGGER.warn(
                "Language server {} for workspace {} exited with code 13 before "
                    + "initialization; retrying with a compatible version. On institutional machines "
                    + "with a fixed Java runtime this is a known issue.",
                languageId,
                root);
            closeActiveClient();
            startAttempt(fallbackCommand, attempt + 1);
            return;
          }
        }
        LOGGER.warn(
            "Language server {} for workspace {} cannot run with the Java runtime in use. "
                + "No compatible fallback is available. On institutional machines with a fixed Java "
                + "runtime this is a known issue; install a compatible JDK or set "
                + "-Dsimpleide.lsp.javaHome.",
            languageId,
            root);
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
          client.completeExceptionally(
              new CancellationException("Language server startup was cancelled."));
        }
        closeActiveClient();
      }
      ThreadMaster threadMaster = ThreadMaster.getInstance();
      try {
        threadMaster.executeOnEDT(() -> threadMaster.removeNodeCascade(startupNode));
      } catch (RuntimeException error) {
        LOGGER.warn("Unable to unregister completed language server startup task.", error);
        threadMaster.removeNodeCascade(startupNode);
      }
    }
  }

  private record ServerKey(String languageId, Path workspaceRoot) {}
}
