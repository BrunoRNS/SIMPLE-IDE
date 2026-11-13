package io.simpleide.lsp.dowload;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.LspStatus;
import io.simpleide.language.config.LangPlatform;

public class LspManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LspManager.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)*)(?!\\d)");

    public static List<String> getComandRun(String language) {
        return getComandRun(language, null);
    }

    public static List<String> getComandRun(String language, Path workspaceRoot) {
        return getComandRun(language, workspaceRoot, language, ignored -> { });
    }

    public static List<String> getComandRun(
            String language,
            Path workspaceRoot,
            String statusId,
            Consumer<LspStatus> statusListener) {
        List<LspServer> candidates = getRegistry().getServers().stream()
                .filter(server -> server.getLanguageId().equals(language)
                        && server.isSupportedOnCurrentPlatform())
                .toList();
        LspServer server = selectServer(candidates);
        if (server == null) {
            publishStatus(statusListener, statusId, language, LspStatus.State.IDLE);
            return new ArrayList<>();
        }

        publishStatus(statusListener, statusId, server.getName(), LspStatus.State.CHECKING_VERSION);
        String detectedVersion = checkMinimumVersion(server);
        LspReleaseResolver.refresh(server);
        if (LspInstaller.isInstalled(server)) {
            return createCommand(server, workspaceRoot);
        }
        if (LspInstaller.dowloading(server, statusId, statusListener)) {
            return createCommand(server, workspaceRoot);
        }
        throw new IllegalStateException(
                "Unable to install language server " + server.getName()
                        + (detectedVersion == null ? "" : " (language version " + detectedVersion + ")"));
    }

    private static LspServer selectServer(List<LspServer> candidates) {
        List<LspServer> versionedCandidates = candidates.stream()
                .filter(server -> server.getMinimumVersion() != null
                        && !server.getMinimumVersion().isBlank())
                .toList();
        if (versionedCandidates.size() < 2) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        LspServer versionProbe = versionedCandidates.stream()
                .filter(server -> server.getVersionCommand() != null
                        && !server.getVersionCommand().isBlank())
                .findFirst()
                .orElse(null);
        if (versionProbe == null) {
            return candidates.get(0);
        }

        return selectClosestCompatibleServer(versionedCandidates, runVersionCommand(versionProbe));
    }

    static LspServer selectClosestCompatibleServer(List<LspServer> candidates, String detectedVersion) {
        LspServer selected = null;
        for (LspServer candidate : candidates) {
            if (compareVersions(detectedVersion, candidate.getMinimumVersion()) < 0) {
                continue;
            }
            if (selected == null
                    || compareVersions(candidate.getMinimumVersion(), selected.getMinimumVersion()) > 0) {
                selected = candidate;
            }
        }
        if (selected != null) {
            return selected;
        }
        return candidates.stream()
                .min((first, second) -> compareVersions(
                        first.getMinimumVersion(), second.getMinimumVersion()))
                .orElseThrow();
    }

    private static void publishStatus(
            Consumer<LspStatus> statusListener, String id, String serverName, LspStatus.State state) {
        if (statusListener != null) {
            statusListener.accept(new LspStatus(id, serverName, state));
        }
    }

    public static Optional<String> getLanguageVersion(String language) {
        for (LspServer server : getRegistry().getServers()) {
            if (!server.getLanguageId().equals(language)
                    || !server.isSupportedOnCurrentPlatform()
                    || server.getVersionCommand() == null
                    || server.getVersionCommand().isBlank()) {
                continue;
            }
            return Optional.of(runVersionCommand(server));
        }
        return Optional.empty();
    }

    static List<String> createCommand(LspServer server, Path workspaceRoot) {
        if (server.getCommand() == null || server.getCommand().isBlank()) {
            throw new IllegalStateException("No launch command configured for " + server.getName());
        }
        Path installDirectory = LspInstaller.getInstallDirectory(server);
        String workspaceKey = workspaceRoot == null
                ? "default"
                : workspaceRoot.toAbsolutePath().normalize().toString();
        Path workspaceData = Paths.get(
                System.getProperty("user.home"),
                ".cache",
                "simpleide",
                "lsp",
                UUID.nameUUIDFromBytes(workspaceKey.getBytes(StandardCharsets.UTF_8)).toString());
        String platformConfiguration = switch (LangPlatform.getCurrentPlatform().getOs()) {
            case "windows" -> "config_win";
            case "macos" -> "config_mac";
            case "linux" -> "config_linux";
            default -> throw new IllegalStateException("Unsupported platform for language server.");
        };

        List<String> command = new ArrayList<>();
        command.addAll(resolveArgument(
                server.getCommand(), installDirectory, workspaceData, server.getVersion(), platformConfiguration));
        if (server.getArguments() != null) {
            for (String argument : server.getArguments()) {
                command.addAll(resolveArgument(
                        argument, installDirectory, workspaceData, server.getVersion(), platformConfiguration));
            }
        }
        return command;
    }

    private static List<String> resolveArgument(
            String argument,
            Path installDirectory,
            Path workspaceData,
            String version,
            String platformConfiguration) {
        if (argument.contains("${workspaceData}")) {
            try {
                Files.createDirectories(workspaceData);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create LSP workspace data directory.", e);
            }
        }
        String resolved = argument
                .replace("${installDir}", installDirectory.toAbsolutePath().toString())
                .replace("${workspaceData}", workspaceData.toString())
                .replace("${version}", version)
                .replace("${platformConfig}", platformConfiguration);
        if (resolved.contains("${")) {
            throw new IllegalStateException("Unresolved LSP command placeholder: " + resolved);
        }
        if (resolved.startsWith("~/")) {
            resolved = Paths.get(System.getProperty("user.home"), resolved.substring(2)).toString();
        }

        String globMarker = containsGlob(resolved) ? createGlobMarker(resolved) : null;
        Path path = Paths.get(globMarker == null ? resolved : escapeGlobCharacters(resolved, globMarker));
        if (containsGlob(resolved)
                && (path.isAbsolute() || resolved.contains("/") || resolved.contains("\\"))) {
            return expandGlob(path, installDirectory, globMarker);
        }
        if (path.isAbsolute()) {
            return List.of(path.normalize().toString());
        }

        Path installedPath = installDirectory.resolve(path).normalize();
        if (installedPath.startsWith(installDirectory.toAbsolutePath().normalize())
                && Files.exists(installedPath)) {
            return List.of(installedPath.toAbsolutePath().toString());
        }
        return List.of(resolved);
    }

    private static boolean containsGlob(String value) {
        return value.contains("*") || value.contains("?") || value.contains("[");
    }

    private static String createGlobMarker(String pattern) {
        String marker;
        do {
            marker = "__SIMPLEIDE_GLOB_" + UUID.randomUUID().toString().replace("-", "") + "_";
        } while (pattern.contains(marker));
        return marker;
    }

    private static String escapeGlobCharacters(String value, String marker) {
        return value.replace("*", marker + "STAR__")
                .replace("?", marker + "QUESTION__")
                .replace("[", marker + "OPEN_BRACKET__")
                .replace("]", marker + "CLOSE_BRACKET__");
    }

    private static String restoreGlobCharacters(String value, String marker) {
        return value.replace(marker + "STAR__", "*")
                .replace(marker + "QUESTION__", "?")
                .replace(marker + "OPEN_BRACKET__", "[")
                .replace(marker + "CLOSE_BRACKET__", "]");
    }

    private static List<String> expandGlob(Path pattern, Path installDirectory, String marker) {
        Path root = installDirectory.toAbsolutePath().normalize();
        Path normalizedPattern = pattern.normalize();
        if (normalizedPattern.isAbsolute()) {
            if (!normalizedPattern.startsWith(root)) {
                throw new IllegalStateException("LSP glob must be inside its installation directory: " + pattern);
            }
            normalizedPattern = root.relativize(normalizedPattern);
        }

        String globPattern = restoreGlobCharacters(normalizedPattern.toString(), marker);
        if ("\\".equals(root.getFileSystem().getSeparator())) {
            globPattern = globPattern.replace("\\", "\\\\");
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        try (var paths = Files.walk(root)) {
            List<String> matches = paths
                    .filter(path -> matcher.matches(root.relativize(path)))
                    .sorted()
                    .map(path -> path.toAbsolutePath().toString())
                    .toList();
            if (matches.isEmpty()) {
                throw new IllegalStateException("LSP command pattern did not match any installed file: " + pattern);
            }
            return matches;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to expand LSP command pattern: " + pattern, e);
        }
    }

    private static String checkMinimumVersion(LspServer server) {
        String minimumVersion = server.getMinimumVersion();
        String versionCommand = server.getVersionCommand();
        if (minimumVersion == null || minimumVersion.isBlank()
                || versionCommand == null || versionCommand.isBlank()) {
            return null;
        }

        String detectedVersion = runVersionCommand(server);
        if (compareVersions(detectedVersion, minimumVersion) < 0) {
            throw new IllegalStateException(
                    server.getName() + " requires language version " + minimumVersion
                            + " or newer; detected " + detectedVersion + ".");
        }
        return detectedVersion;
    }

    private static String runVersionCommand(LspServer server) {
        List<String> command = new ArrayList<>();
        command.add(server.getVersionCommand());
        if (server.getVersionArguments() != null) {
            command.addAll(server.getVersionArguments());
        }

        Path outputFile;
        try {
            outputFile = Files.createTempFile("simpleide-lsp-version-", ".log");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to create output file for language version command: " + command, e);
        }

        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Language version command timed out: " + command);
            }
            String result = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "Language version command failed with exit code "
                                + process.exitValue() + ": " + result);
            }
            if (result.isBlank()) {
                throw new IllegalStateException(
                        "Language version command returned no output: " + command);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking language version.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read language version command output.", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            try {
                Files.deleteIfExists(outputFile);
            } catch (IOException error) {
                outputFile.toFile().deleteOnExit();
                LOGGER.warn("Unable to delete language version output file {}.", outputFile, error);
            }
        }
    }

    static int compareVersions(String actual, String minimum) {
        int[] actualParts = extractVersion(actual);
        int[] minimumParts = extractVersion(minimum);
        for (int index = 0; index < Math.max(actualParts.length, minimumParts.length); index++) {
            int actualPart = index < actualParts.length ? actualParts[index] : 0;
            int minimumPart = index < minimumParts.length ? minimumParts[index] : 0;
            if (actualPart != minimumPart) {
                return Integer.compare(actualPart, minimumPart);
            }
        }
        return 0;
    }

    private static int[] extractVersion(String value) {
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.find()) {
            throw new IllegalArgumentException("No numeric version found in: " + value);
        }
        String[] parts = matcher.group(1).split("\\.");
        int[] version = new int[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                version[index] = Integer.parseInt(parts[index]);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version: " + value, e);
        }
        return version;
    }

    public static LspRegistry getRegistry() {
        try (InputStream inputStream = LspManager.class.getResourceAsStream(
                "/io/simpleide/lsp/dowload/registry.json")) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "registry.json not found!");
            }

            try (JsonReader reader = Json.createReader(inputStream)) {

                JsonObject root = reader.readObject();
                JsonArray servers = root.getJsonArray("servers");

                List<LspServer> serverList = new ArrayList<>();

                for (JsonObject server : servers.getValuesAs(JsonObject.class)) {

                    List<String> os = readStringList(server, "os");
                    List<String> arch = readStringList(server, "arch");
                    List<String> arguments = readStringList(server, "arguments");
                    List<String> installArguments = readStringList(server, "installArguments");
                    List<String> postInstallArguments = readStringList(server, "postInstallArguments");
                    List<String> versionArguments = readStringList(server, "versionArguments");
                    List<String> paramsArchiverCommand = readStringList(server, "paramsArchiverCommand");

                    LspServer lsp = new LspServer(
                            server.getString("id"),
                            server.getString("version"),
                            server.getString("name"),
                            server.getString("languageId"),
                            os,
                            arch,
                            server.getString("minimumVersion", null),
                            server.getString("url", null),
                            server.getString("command", null),
                            arguments,
                            server.getString("installCommand", null),
                            installArguments,
                            server.getString("typeFile", null),
                            server.getString("versionCommand", null),
                            versionArguments,
                            paramsArchiverCommand,
                            server.getString("archiverCommand", null)

                        );
                    lsp.setInstallKey(server.getString("installKey", null));
                    lsp.setPostInstallCommand(server.getString("postInstallCommand", null));
                    lsp.setPostInstallArguments(postInstallArguments);
                    lsp.setLatestVersionApi(server.getString("latestVersionApi", null));
                    lsp.setLatestVersionPath(server.getString("latestVersionPath", null));
                    lsp.setLatestVersionListPath(server.getString("latestVersionListPath", null));
                    lsp.setLatestAssetName(server.getString("latestAssetName", null));
                    lsp.setLatestVersionUrlTemplate(server.getString("latestVersionUrlTemplate", null));
                    lsp.setInstallEnvironment(readStringMap(server, "installEnvironment"));

                    serverList.add(lsp);
                }

                return new LspRegistry(serverList);
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error to load registry.json", e);
        }
    }

    private static List<String> readStringList(
            JsonObject object,
            String key) {
        if (!object.containsKey(key)
                || object.isNull(key)) {
            return new ArrayList<>();
        }

        return object.getJsonArray(key)
                .getValuesAs(JsonString.class)
                .stream()
                .map(JsonString::getString)
                .toList();
    }

    private static Map<String, String> readStringMap(JsonObject object, String key) {
        JsonObject values = object.getJsonObject(key);
        if (values == null) {
            return Map.of();
        }
        return values.keySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        keyName -> keyName, values::getString));
    }
}
