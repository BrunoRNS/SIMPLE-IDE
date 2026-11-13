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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LspReleaseResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(LspReleaseResolver.class);
    private static final Set<String> API_HOSTS = Set.of(
            "api.github.com",
            "api.nuget.org",
            "proxy.golang.org",
            "registry.npmjs.org");
    private static final long SUCCESS_CACHE_NANOS = TimeUnit.HOURS.toNanos(6);
    private static final long FAILURE_CACHE_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final ConcurrentMap<String, CachedRelease> RELEASE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Object> REFRESH_LOCKS = new ConcurrentHashMap<>();
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

        String key = server.getId() == null
                ? server.getLanguageId() + ":" + server.getName()
                : server.getId();
        CachedRelease cached = RELEASE_CACHE.get(key);
        if (isFresh(cached)) {
            cached.applyTo(server);
            return;
        }

        synchronized (REFRESH_LOCKS.computeIfAbsent(key, ignored -> new Object())) {
            cached = RELEASE_CACHE.get(key);
            if (isFresh(cached)) {
                cached.applyTo(server);
                return;
            }
            boolean refreshed = refreshFromApi(server);
            RELEASE_CACHE.put(key, new CachedRelease(
                    server.getVersion(),
                    server.getUrl(),
                    System.nanoTime(),
                    refreshed ? SUCCESS_CACHE_NANOS : FAILURE_CACHE_NANOS));
        }
    }

    private static boolean isFresh(CachedRelease cached) {
        return cached != null && System.nanoTime() - cached.refreshedAtNanos < cached.ttlNanos;
    }

    private static boolean refreshFromApi(LspServer server) {
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
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while checking the latest version of LSP {}. Using registry version {}.",
                    server.getName(), server.getVersion());
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Unable to check the latest version of LSP {}. Using registry version {}.",
                    server.getName(), server.getVersion(), error);
        }
        return false;
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

    private record CachedRelease(String version, String url, long refreshedAtNanos, long ttlNanos) {
        private void applyTo(LspServer server) {
            server.setVersion(version);
            server.setUrl(url);
        }
    }
}
