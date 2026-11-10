package io.simpleide.lsp.dowload;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LspManager {

    public static String getComandRun(String language) {
        LspRegistry lspRegistry = getRegistry();
        for (LspServer server : lspRegistry.getServers()) {
            if (server.getLanguageId().equals(language) && server.isSupportedOnCurrentPlatform()) {
                if (existisLsp(language + server.getVersion())) {

                }
                LspInstaller.dowloading(server);
                
            }
        }
        return "";
    }

    public static LspRegistry getRegistry() {
        try (InputStream inputStream = LspManager.class.getResourceAsStream("/registry.json")) {

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
                            arguments);

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

    private static boolean existisLsp(String li) {
        String home = System.getProperty("user.home");
        Path path = Paths.get(home, ".simpleide", "lsp", li);
        return Files.exists(path);

    }
}
