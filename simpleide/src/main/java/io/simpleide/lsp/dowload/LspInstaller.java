package io.simpleide.lsp.dowload;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.LspStatus;

public final class LspInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(LspInstaller.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ConcurrentMap<Path, Object> INSTALL_LOCKS = new ConcurrentHashMap<>();

    public static boolean dowloading(LspServer server) {
        return dowloading(server, server.getLanguageId(), ignored -> { });
    }

    public static boolean dowloading(
            LspServer server, String statusId, Consumer<LspStatus> statusListener) {
        Path lspDirectory = getInstallDirectory(server);
        Object installLock = INSTALL_LOCKS.computeIfAbsent(lspDirectory, ignored -> new Object());
        synchronized (installLock) {
            return install(server, lspDirectory, statusId, statusListener);
        }
    }

    private static boolean install(
            LspServer server, Path lspDirectory, String statusId, Consumer<LspStatus> statusListener) {
        try {
            if (isInstalled(server)) {
                return true;
            }
            deleteDirectory(lspDirectory);
            Files.createDirectories(lspDirectory);

            if (server.getInstallCommand() != null) {
                publishStatus(statusListener, statusId, server.getName(), LspStatus.State.INSTALLING);
                dowloadingByCommand(
                        server.getInstallCommand(), server.getInstallArguments(), lspDirectory, server.getVersion(),
                        server.getInstallEnvironment());
            } else if (server.getArchiverCommand() != null) {
                publishStatus(statusListener, statusId, server.getName(), LspStatus.State.INSTALLING);
                dowloadingByCommand(
                        server.getArchiverCommand(), server.getParamsArchiverCommand(), lspDirectory,
                        server.getVersion(), server.getInstallEnvironment());
            } else {
                publishStatus(statusListener, statusId, server.getName(), LspStatus.State.DOWNLOADING);
                Path archive = dowloadingByUrl(server, lspDirectory);
                try {
                    publishStatus(statusListener, statusId, server.getName(), LspStatus.State.EXTRACTING);
                    descompact(archive, lspDirectory);
                } finally {
                    Files.deleteIfExists(archive);
                }
            }

            if (server.getPostInstallCommand() != null) {
                publishStatus(statusListener, statusId, server.getName(), LspStatus.State.INSTALLING);
                dowloadingByCommand(
                        server.getPostInstallCommand(),
                        server.getPostInstallArguments(),
                        lspDirectory,
                        server.getVersion(),
                        server.getInstallEnvironment());
            }

            Files.writeString(
                    lspDirectory.resolve(".installed"),
                    server.getVersion(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("LSP {} installed successfully.", server.getName());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failInstallation(server, lspDirectory, statusId, statusListener, e);
        } catch (IOException | RuntimeException e) {
            return failInstallation(server, lspDirectory, statusId, statusListener, e);
        }
    }

    public static boolean isInstalled(LspServer server) {
        Path marker = getInstallDirectory(server).resolve(".installed");
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        try {
            return Files.readString(marker).trim().equals(server.getVersion());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read installed LSP version: " + marker, e);
        }
    }

    static Path getInstallDirectory(LspServer server) {
        String installKey = server.getInstallKey();
        if (installKey != null && !installKey.isBlank()
                && !installKey.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid LSP installation key: " + installKey);
        }
        return Paths.get(
                System.getProperty("user.home"),
                ".simpleide",
                "lsp",
                installKey == null || installKey.isBlank()
                        ? server.getLanguageId() + server.getVersion()
                        : installKey);
    }

    private static boolean failInstallation(
            LspServer server,
            Path directory,
            String statusId,
            Consumer<LspStatus> statusListener,
            Exception error) {
        try {
            deleteDirectory(directory);
        } catch (IOException cleanupError) {
            error.addSuppressed(cleanupError);
        }
        LOGGER.error("Unable to install LSP {}.", server.getName(), error);
        publishStatus(statusListener, statusId, server.getName(), LspStatus.State.FAILED);
        return false;
    }

    private static void publishStatus(
            Consumer<LspStatus> statusListener, String id, String serverName, LspStatus.State state) {
        if (statusListener != null) {
            statusListener.accept(new LspStatus(id, serverName, state));
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void descompact(Path path, Path destination) throws IOException {
        Archiver archiver = ArchiverFactory.createArchiver(path.toFile());
        archiver.extract(path.toFile(), destination.toFile());
    }

    private static Path dowloadingByUrl(LspServer lspServer, Path lsp)
            throws IOException, InterruptedException {
        if (lspServer.getUrl() == null || lspServer.getUrl().isBlank()
                || lspServer.getTypeFile() == null || lspServer.getTypeFile().isBlank()) {
            throw new IOException("LSP URL or archive type is not configured.");
        }
        Path destination = Files.createTempFile(
                lsp, "lsp-download.", "." + lspServer.getTypeFile());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(lspServer.getUrl()))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "SimpleIDE")
                .GET()
                .build();
        try {
            HttpResponse<Path> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(
                            destination,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(
                        "HTTP download failed: " + response.statusCode());
            }

            LOGGER.info("LSP downloaded successfully: {}", destination);
            return destination;

        } catch (IOException | InterruptedException | RuntimeException e) {
            Files.deleteIfExists(destination);
            throw e;
        }
    }

    private static void dowloadingByCommand(
            String command, List<String> params, Path directory, String version, Map<String, String> environment)
            throws IOException, InterruptedException {
        if (command.isBlank()) {
            throw new IOException("LSP install command is empty.");
        }
        String installDirectory = directory.toAbsolutePath().toString();
        List<String> full = new ArrayList<>();
        full.add(command.replace("${installDir}", installDirectory).replace("${version}", version));
        if (params != null) {
            params.stream()
                    .map(argument -> argument.replace("${installDir}", installDirectory)
                            .replace("${version}", version))
                    .forEach(full::add);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(full)
                .directory(directory.toFile())
                .inheritIO();
        if (environment != null) {
            environment.forEach((key, value) -> processBuilder.environment().put(
                    key,
                    value.replace("${installDir}", installDirectory).replace("${version}", version)));
        }
        Process process = processBuilder.start();
        try {
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("LSP install command timed out: " + command);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
        if (process.exitValue() != 0) {
            throw new IOException("LSP install command failed with exit code "
                    + process.exitValue() + ": " + command);
        }
    }
}
