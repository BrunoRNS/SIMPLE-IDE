package io.simpleide.dap;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.simpleide.language.config.LangPlatform;

/**
 * Debug adapter definitions loaded from the bundled {@code registry.json}.
 *
 * <p>
 * The registry maps each operating system and architecture to the download URL of a
 * compatible debug adapter. It is parsed without network access, so menus can decide
 * whether debugging is supported before any download is attempted.
 *
 * <p>
 * Covered by {@code DapRegistryTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class DapRegistry {

	/**
	 * Classpath location of the bundled registry.
	 */
	public static final String RESOURCE = "/io/simpleide/dap/registry.json";

	private final List<DapAdapter> adapters;

	/**
	 * Creates a registry backed by adapter definitions.
	 * @param adapters adapter definitions
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapRegistry(List<DapAdapter> adapters) {
		this.adapters = List.copyOf(adapters == null ? List.of() : adapters);
	}

	/**
	 * Loads the registry bundled in the application classpath.
	 * @return the parsed registry
	 * @throws IllegalStateException when the resource is missing or malformed
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static DapRegistry loadDefault() {
		try (InputStream input = DapRegistry.class.getResourceAsStream(RESOURCE)) {
			if (input == null) {
				throw new IllegalStateException("DAP registry resource not found: " + RESOURCE);
			}
			return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException error) {
			throw new IllegalStateException("Unable to read the DAP registry resource.", error);
		}
	}

	/**
	 * Parses a registry JSON document.
	 * @param json JSON text
	 * @return the parsed registry
	 * @throws IllegalArgumentException when the document is not a valid registry
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static DapRegistry parse(String json) {
		Objects.requireNonNull(json, "json");
		try (JsonReader reader = Json.createReader(new StringReader(json))) {
			JsonObject root = reader.readObject();
			JsonArray array = root.getJsonArray("adapters");
			if (array == null) {
				throw new IllegalArgumentException("DAP registry has no adapters array.");
			}
			List<DapAdapter> adapters = new ArrayList<>();
			for (JsonValue value : array) {
				if (value.getValueType() != JsonValue.ValueType.OBJECT) {
					throw new IllegalArgumentException("DAP registry adapters must be objects.");
				}
				adapters.add(parseAdapter(value.asJsonObject()));
			}
			return new DapRegistry(adapters);
		}
		catch (IllegalArgumentException error) {
			throw error;
		}
		catch (RuntimeException error) {
			throw new IllegalArgumentException("Malformed DAP registry JSON.", error);
		}
	}

	private static DapAdapter parseAdapter(JsonObject object) {
		String id = requiredString(object, "id");
		String name = requiredString(object, "name");
		String command = requiredString(object, "command");
		String version = optionalString(object, "version", "");
		String transport = optionalString(object, "transport", "stdio");
		List<String> arguments = readStringList(object, "arguments");
		JsonArray downloadsArray = object.getJsonArray("downloads");
		if (downloadsArray == null) {
			throw new IllegalArgumentException("DAP adapter " + id + " has no downloads array.");
		}
		List<DapDownload> downloads = new ArrayList<>();
		for (JsonValue value : downloadsArray) {
			if (value.getValueType() != JsonValue.ValueType.OBJECT) {
				throw new IllegalArgumentException("DAP adapter downloads must be objects.");
			}
			JsonObject download = value.asJsonObject();
			downloads.add(new DapDownload(requiredString(download, "os"), requiredString(download, "arch"),
					requiredString(download, "url"), optionalString(download, "archiveType", null)));
		}
		return new DapAdapter(id, name, version, command, arguments, downloads, transport,
				readStringMap(object, "launch"));
	}

	private static String requiredString(JsonObject object, String key) {
		String value = optionalString(object, key, null);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("DAP registry property is missing: " + key);
		}
		return value;
	}

	private static String optionalString(JsonObject object, String key, String fallback) {
		JsonValue value = object.get(key);
		if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
			return fallback;
		}
		if (value.getValueType() != JsonValue.ValueType.STRING) {
			throw new IllegalArgumentException("DAP registry property must be a string: " + key);
		}
		return ((JsonString) value).getString();
	}

	private static List<String> readStringList(JsonObject object, String key) {
		JsonArray array = object.getJsonArray(key);
		if (array == null) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (JsonValue value : array) {
			if (value.getValueType() != JsonValue.ValueType.STRING) {
				throw new IllegalArgumentException("DAP registry list must contain strings: " + key);
			}
			values.add(((JsonString) value).getString());
		}
		return values;
	}

	private static Map<String, String> readStringMap(JsonObject object, String key) {
		JsonObject source = object.getJsonObject(key);
		if (source == null) {
			return Map.of();
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonValue> entry : source.entrySet()) {
			JsonValue value = entry.getValue();
			if (value.getValueType() != JsonValue.ValueType.STRING) {
				throw new IllegalArgumentException("DAP registry launch properties must be strings: " + key);
			}
			values.put(entry.getKey(), ((JsonString) value).getString());
		}
		return values;
	}

	/**
	 * Returns the first adapter that supports a platform.
	 * @param platform platform to resolve
	 * @return the matching adapter, or {@code null} when none supports the platform
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapAdapter findForPlatform(LangPlatform platform) {
		for (DapAdapter adapter : adapters) {
			if (adapter.downloadFor(platform) != null) {
				return adapter;
			}
		}
		return null;
	}

	/**
	 * Returns whether a platform has a registered debug adapter.
	 * @param platform platform to check
	 * @return {@code true} when an adapter and a download entry exist
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean supports(LangPlatform platform) {
		return findForPlatform(platform) != null;
	}

	/**
	 * Returns every adapter definition in registry order.
	 * @return the immutable adapter list
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public List<DapAdapter> getAdapters() {
		return adapters;
	}

}
