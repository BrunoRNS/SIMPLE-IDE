package io.simpleide.lsp.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.dowload.LspInstaller;
import io.simpleide.lsp.dowload.LspManager;

public final class LanguageServerManager implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerManager.class);
  
  private static final Set<String> WORKSPACE_MARKERS = Set.of(
      "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
      "settings.gradle.kts", "package.json", "pyproject.toml", "setup.py",
      "Cargo.toml", "go.mod", "compile_commands.json");

  private final Map<ServerKey, ServerEntry> servers = new ConcurrentHashMap<>();


  public CompletableFuture<LspClient> connect(
      Document document, Consumer<PublishDiagnosticsParams> diagnosticsHandler) {

    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(diagnosticsHandler, "diagnosticsHandler");

    Path root = findWorkspaceRoot(document.getPath());
    String command;
    Map<String, String> environment;

    try {
      command = LspManager.getComandRun(document.getLanguageId());
      environment = getEnvironment(document.getLanguageId());
    } catch (IllegalArgumentException | IllegalStateException error) {
      return CompletableFuture.failedFuture(error);
    }
    if (command.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    ServerKey key = new ServerKey(document.getLanguageId(), root);
    ServerEntry entry = servers.computeIfAbsent(key, ignored -> new ServerEntry(command, root, environment));
    entry.diagnosticsHandlers.put(document.getUri(), diagnosticsHandler);
    return entry.client
        .thenCompose(client -> client.getInitialized().thenApply(ignored -> client))
        .whenComplete((client, error) -> {
          if (error != null) {
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
    servers.values().forEach(entry -> entry.client.whenComplete((client, error) -> {
      if (client != null) {
        client.close();
      }
    }));
    servers.clear();
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
    private final Map<String, Consumer<PublishDiagnosticsParams>> diagnosticsHandlers = new ConcurrentHashMap<>();
    private final CompletableFuture<LspClient> client;

    private ServerEntry(String command, Path root, Map<String, String> environment) {
      client = CompletableFuture.supplyAsync(() -> {
        try {
          return new LspClient(command, root, diagnostics -> {
            Consumer<PublishDiagnosticsParams> handler = diagnosticsHandlers.get(diagnostics.getUri());
            if (handler != null) {
              handler.accept(diagnostics);
            }
          }, environment);
        } catch (IOException error) {
          throw new java.util.concurrent.CompletionException(error);
        }
      }).whenComplete((lspClient, error) -> {
        if (error != null) {
          LOGGER.error("Unable to start language server {} for workspace {}.",
              command, root, error);
        }
      });
    }
  }

  private record ServerKey(String languageId, Path workspaceRoot) {
  }
}
