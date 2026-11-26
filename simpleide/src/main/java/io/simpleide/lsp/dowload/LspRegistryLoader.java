package io.simpleide.lsp.dowload;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;

/** Loads the bundled language server registry. */
/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LspRegistryLoader {

	private LspRegistryLoader() {
	}

	static LspRegistry load() {
		try (InputStream inputStream = LspRegistryLoader.class
			.getResourceAsStream("/io/simpleide/lsp/dowload/registry.json")) {
			if (inputStream == null) {
				throw new IllegalStateException("registry.json not found!");
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
					List<String> archiverArguments = readStringList(server, "paramsArchiverCommand");
					LspServer serverData = new LspServer(server.getString("id"), server.getString("version"),
							server.getString("name"), server.getString("languageId"), os, arch,
							server.getString("minimumVersion", null), server.getString("url", null),
							server.getString("command", null), arguments, server.getString("installCommand", null),
							installArguments, server.getString("typeFile", null),
							server.getString("versionCommand", null), versionArguments, archiverArguments,
							server.getString("archiverCommand", null));
					serverData.setInstallKey(server.getString("installKey", null));
					serverData.setPostInstallCommand(server.getString("postInstallCommand", null));
					serverData.setPostInstallArguments(postInstallArguments);
					serverData.setLatestVersionApi(server.getString("latestVersionApi", null));
					serverData.setLatestVersionPath(server.getString("latestVersionPath", null));
					serverData.setLatestVersionListPath(server.getString("latestVersionListPath", null));
					serverData.setLatestAssetName(server.getString("latestAssetName", null));
					serverData.setLatestVersionUrlTemplate(server.getString("latestVersionUrlTemplate", null));
					serverData.setInstallEnvironment(readStringMap(server, "installEnvironment"));
					serverList.add(serverData);
				}
				return new LspRegistry(serverList);
			}
		}
		catch (Exception error) {
			throw new RuntimeException("Error to load registry.json", error);
		}
	}

	private static List<String> readStringList(JsonObject object, String key) {
		if (!object.containsKey(key) || object.isNull(key)) {
			return new ArrayList<>();
		}
		return object.getJsonArray(key).getValuesAs(JsonString.class).stream().map(JsonString::getString).toList();
	}

	private static Map<String, String> readStringMap(JsonObject object, String key) {
		JsonObject values = object.getJsonObject(key);
		if (values == null) {
			return Map.of();
		}
		return values.keySet().stream().collect(Collectors.toUnmodifiableMap(name -> name, values::getString));
	}

}
