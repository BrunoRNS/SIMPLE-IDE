package io.simpleide.lsp.core;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

public final class LanguageServerManager implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerManager.class);
  private static final Path AMBUS_HOME = resolveAmbusHome();
  private static final Map<String, List<String>> DEFAULT_COMMANDS = Map.of(
      "kotlin", List.of("kotlin-language-server"),
      "groovy", List.of("groovy-language-server"),
      "javascript", List.of("typescript-language-server", "--stdio"),
      "python", List.of("pyright-langserver", "--stdio"),
      "c", List.of("clangd", "--background-index"),
      "cpp", List.of("clangd", "--background-index"));
  private static final Set<String> WORKSPACE_MARKERS = Set.of(
      "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
      "settings.gradle.kts", "package.json", "pyproject.toml", "setup.py",
      "Cargo.toml", "go.mod", "compile_commands.json");

  private final Map<ServerKey, ServerEntry> servers = new ConcurrentHashMap<>();

  private static Path resolveAmbusHome() {
    String configuredHome = System.getProperty("simpleide.lsp.ambus.home");
    if (configuredHome != null && !configuredHome.isBlank()) {
      try {
        Path configuredPath = Path.of(configuredHome).toAbsolutePath().normalize();
        if (isAmbusHome(configuredPath)) {
          return configuredPath;
        }
        LOGGER.warn("Configured Ambus directory is invalid: {}", configuredPath);
      } catch (InvalidPathException error) {
        LOGGER.warn("Configured Ambus path is invalid: {}", configuredHome, error);
      }
      return null;
    }

    try {
      Path location = Path.of(
          LanguageServerManager.class.getProtectionDomain().getCodeSource().getLocation().toURI())
          .toAbsolutePath().normalize();
      if (Files.isRegularFile(location)) {
        location = location.getParent();
      }

      for (Path current = location; current != null; current = current.getParent()) {
        Path reactorDistribution = current.resolve("ambus/target/lsp_configurado");
        if (isAmbusHome(reactorDistribution)) {
          return reactorDistribution;
        }

        Path installedDistribution = current.resolve("ambus");
        if (isAmbusHome(installedDistribution)) {
          return installedDistribution;
        }
      }

      LOGGER.debug("Ambus was not found. Configure simpleide.lsp.ambus.home to enable Java LSP.");
      return null;
    } catch (URISyntaxException | SecurityException error) {
      LOGGER.warn("Unable to locate the Ambus Java language server.", error);
      return null;
    }
  }

  private static boolean isAmbusHome(Path path) {
    return Files.isDirectory(path.resolve("plugins"))
        && Files.isDirectory(path.resolve("config_ativa"));
  }

  public CompletableFuture<LspClient> connect(
      Document document, Consumer<PublishDiagnosticsParams> diagnosticsHandler) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(diagnosticsHandler, "diagnosticsHandler");
    Path root = findWorkspaceRoot(document.getPath());
    List<String> command;
    Map<String, String> environment;
    try {
      command = getCommand(document.getLanguageId(), root);
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

  private List<String> getCommand(String languageId, Path workspaceRoot) {
    String property = System.getProperty("simpleide.lsp.command." + languageId);
    if (property != null) {
      return parseCommand(property);
    }

    if ("java".equals(languageId)) {
      if (AMBUS_HOME == null) {
        throw new IllegalStateException(
            "Ambus was not found. Configure simpleide.lsp.ambus.home.");
      }
      return getAmbusCommand(workspaceRoot);
    }

    return DEFAULT_COMMANDS.getOrDefault(languageId, List.of());
  }

  private List<String> getAmbusCommand(Path workspaceRoot) {
    boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    String executable = Path.of(
        System.getProperty("java.home"),
        "bin",
        windows ? "java.exe" : "java").toString();
    String workspaceName = workspaceRoot == null
        ? "default"
        : Integer.toHexString(workspaceRoot.toString().hashCode());
    Path serverWorkspace = Path.of(
        System.getProperty("user.home"),
        ".simpleide",
        "ambus-workspaces",
        workspaceName);

    return List.of(
        executable,
        "-Declipse.application=org.eclipse.jdt.ls.core.id1",
        "-Dosgi.bundles.defaultStartLevel=4",
        "-Declipse.product=org.eclipse.jdt.ls.core.product",
        "-Xmx1G",
        "-cp",
        AMBUS_HOME.resolve("plugins").resolve("*").toString(),
        "org.eclipse.equinox.launcher.Main",
        "-configuration",
        AMBUS_HOME.resolve("config_ativa").toString(),
        "-data",
        serverWorkspace.toString());
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

  private List<String> parseCommand(String command) {
    List<String> parts = new ArrayList<>();
    StringBuilder part = new StringBuilder();
    char quote = 0;
    boolean escaping = false;
    for (int index = 0; index < command.length(); index++) {
      char current = command.charAt(index);
      if (escaping) {
        part.append(current);
        escaping = false;
      } else if (current == '\\' && quote != '\'') {
        escaping = true;
      } else if (quote != 0) {
        if (current == quote) {
          quote = 0;
        } else {
          part.append(current);
        }
      } else if (current == '\'' || current == '"') {
        quote = current;
      } else if (Character.isWhitespace(current)) {
        if (!part.isEmpty()) {
          parts.add(part.toString());
          part.setLength(0);
        }
      } else {
        part.append(current);
      }
    }
    if (escaping || quote != 0) {
      throw new IllegalArgumentException("Invalid language server command: " + command);
    }
    if (!part.isEmpty()) {
      parts.add(part.toString());
    }
    return List.copyOf(parts);
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

    private ServerEntry(List<String> command, Path root, Map<String, String> environment) {
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
