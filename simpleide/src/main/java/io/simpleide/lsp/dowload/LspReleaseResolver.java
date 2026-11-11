package io.simpleide.lsp.dowload;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LspReleaseResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(LspReleaseResolver.class);
    private static final Set<String> API_HOSTS = Set.of(
            "api.github.com",
            "api.nuget.org",
            "proxy.golang.org",
            "registry.npmjs.org");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private LspReleaseResolver() {
    }

    static void refresh(LspServer server) {
        if (server.getLatestVersionApi() == null || server.getLatestVersionApi().isBlank()) {
            return;
        }

        try {
            URI apiUri = URI.create(server.getLatestVersionApi());
            if (!"https".equalsIgnoreCase(apiUri.getScheme())
                    || !API_HOSTS.contains(apiUri.getHost())) {
                throw new IOException("Untrusted LSP version API host: " + apiUri.getHost());
            }

            HttpRequest request = HttpRequest.newBuilder(apiUri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SimpleIDE")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Version API returned HTTP " + response.statusCode());
            }

            JsonObject metadata;
            try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
                metadata = reader.readObject();
            }
            String version = readVersion(metadata, server);
            if (!version.matches("[A-Za-z0-9][A-Za-z0-9._+-]*")) {
                throw new IOException("Version API returned an invalid version: " + version);
            }

            String downloadUrl = server.getUrl();
            if (server.getLatestAssetName() != null) {
                String assetName = server.getLatestAssetName().replace("${version}", version);
                JsonArray assets = metadata.getJsonArray("assets");
                if (assets == null) {
                    throw new IOException("Version API response has no release assets.");
                }
                downloadUrl = assets.getValuesAs(JsonObject.class).stream()
                        .filter(asset -> assetName.equals(asset.getString("name", "")))
                        .map(asset -> asset.getString("browser_download_url", ""))
                        .filter(value -> !value.isBlank())
                        .findFirst()
                        .orElseThrow(() -> new IOException("Release asset not found: " + assetName));
                URI assetUri = URI.create(downloadUrl);
                if (!"https".equalsIgnoreCase(assetUri.getScheme())
                        || !"github.com".equalsIgnoreCase(assetUri.getHost())) {
                    throw new IOException("Release API returned an untrusted asset URL.");
                }
            } else if (server.getLatestVersionUrlTemplate() != null) {
                downloadUrl = server.getLatestVersionUrlTemplate().replace("${version}", version);
                URI downloadUri = URI.create(downloadUrl);
                if (!"https".equalsIgnoreCase(downloadUri.getScheme())
                        || !"github.com".equalsIgnoreCase(downloadUri.getHost())) {
                    throw new IOException("Version API configuration contains an untrusted download URL.");
                }
            }

            server.setVersion(version);
            if (downloadUrl != null) {
                server.setUrl(downloadUrl);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while checking the latest version of LSP {}. Using registry version {}.",
                    server.getName(), server.getVersion());
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Unable to check the latest version of LSP {}. Using registry version {}.",
                    server.getName(), server.getVersion(), error);
        }
    }

    private static String readVersion(JsonObject metadata, LspServer server) throws IOException {
        if (server.getLatestVersionPath() != null) {
            String value = metadata.getString(server.getLatestVersionPath(), null);
            if (value == null || value.isBlank()) {
                throw new IOException("Version API response is missing " + server.getLatestVersionPath());
            }
            return value;
        }
        String listPath = server.getLatestVersionListPath();
        JsonArray versions = listPath == null ? null : metadata.getJsonArray(listPath);
        if (versions == null) {
            throw new IOException("Version API response has no configured version list.");
        }
        return versions.getValuesAs(JsonString.class).stream()
                .map(JsonString::getString)
                .filter(version -> !version.contains("-"))
                .max(Comparator.comparing(version -> version, LspManager::compareVersions))
                .orElseThrow(() -> new IOException("Version API returned no stable versions."));
    }
}
