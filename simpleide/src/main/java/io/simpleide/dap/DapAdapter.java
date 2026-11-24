package io.simpleide.dap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.simpleide.language.config.LangPlatform;

/**
 * Debug adapter definition loaded from {@code registry.json}.
 *
 * <p>
 * An adapter declares the launch command, its arguments and the download entry for every
 * supported operating system and architecture. Command arguments support the
 * {@code ${installDir}} and {@code ${program}} placeholders, which are replaced when a
 * session starts.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class DapAdapter {

	private final String id;

	private final String name;

	private final String version;

	private final String languageId;

	private final String command;

	private final List<String> arguments;

	private final List<DapDownload> downloads;

	private final String transport;

	private final Map<String, String> launchArguments;

	private final Map<String, String> environment;

	/**
	 * Creates an adapter definition that communicates over standard input and output.
	 * @param id stable adapter identifier
	 * @param name human-readable adapter name
	 * @param version adapter version
	 * @param command launch command
	 * @param arguments launch arguments
	 * @param downloads platform-specific download entries
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapAdapter(String id, String name, String version, String command, List<String> arguments,
			List<DapDownload> downloads) {
		this(id, name, version, command, arguments, downloads, "stdio", Map.of(), Map.of());
	}

	/**
	 * Creates an adapter definition.
	 * @param id stable adapter identifier
	 * @param name human-readable adapter name
	 * @param version adapter version
	 * @param command launch command
	 * @param arguments launch arguments
	 * @param downloads platform-specific download entries
	 * @param transport {@code stdio} or {@code tcp}
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapAdapter(String id, String name, String version, String command, List<String> arguments,
			List<DapDownload> downloads, String transport) {
		this(id, name, version, command, arguments, downloads, transport, Map.of(), Map.of());
	}

	/**
	 * Creates an adapter definition.
	 * @param id stable adapter identifier
	 * @param name human-readable adapter name
	 * @param version adapter version
	 * @param command launch command
	 * @param arguments launch arguments
	 * @param downloads platform-specific download entries
	 * @param transport {@code stdio} or {@code tcp}
	 * @param launchArguments extra properties merged into the DAP launch request
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapAdapter(String id, String name, String version, String command, List<String> arguments,
			List<DapDownload> downloads, String transport, Map<String, String> launchArguments) {
		this(id, name, version, command, arguments, downloads, transport, launchArguments, Map.of());
	}

	/**
	 * Creates an adapter definition.
	 * @param id stable adapter identifier
	 * @param name human-readable adapter name
	 * @param version adapter version
	 * @param command launch command
	 * @param arguments launch arguments
	 * @param downloads platform-specific download entries
	 * @param transport {@code stdio} or {@code tcp}
	 * @param launchArguments extra properties merged into the DAP launch request
	 * @param environment extra environment variables for the adapter process
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapAdapter(String id, String name, String version, String command, List<String> arguments,
			List<DapDownload> downloads, String transport, Map<String, String> launchArguments,
			Map<String, String> environment) {
		this(id, name, version, null, command, arguments, downloads, transport, launchArguments, environment);
	}

	/**
	 * Creates an adapter definition bound to a language.
	 * @param id stable adapter identifier
	 * @param name human-readable adapter name
	 * @param version adapter version
	 * @param languageId language identifier the adapter debugs, or {@code null}
	 * @param command launch command
	 * @param arguments launch arguments
	 * @param downloads platform-specific download entries
	 * @param transport {@code stdio}, {@code tcp} or {@code jdtls}
	 * @param launchArguments extra properties merged into the DAP launch request
	 * @param environment extra environment variables for the adapter process
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapAdapter(String id, String name, String version, String languageId, String command, List<String> arguments,
			List<DapDownload> downloads, String transport, Map<String, String> launchArguments,
			Map<String, String> environment) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.version = version == null ? "" : version;
		this.languageId = languageId == null ? "" : languageId;
		this.command = Objects.requireNonNull(command, "command");
		this.arguments = List.copyOf(arguments == null ? List.of() : arguments);
		this.downloads = List.copyOf(downloads == null ? List.of() : downloads);
		this.transport = transport == null || transport.isBlank() ? "stdio" : transport.toLowerCase();
		this.launchArguments = Map.copyOf(launchArguments == null ? Map.of() : new LinkedHashMap<>(launchArguments));
		this.environment = Map.copyOf(environment == null ? Map.of() : new LinkedHashMap<>(environment));
	}

	/**
	 * Finds the download entry for a platform.
	 * @param platform platform to resolve
	 * @return the matching download, or {@code null} when the adapter does not support
	 * the platform
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapDownload downloadFor(LangPlatform platform) {
		if (platform == null || platform == LangPlatform.UNKNOWN) {
			return null;
		}
		for (DapDownload download : downloads) {
			if (download.matches(platform.getOs(), platform.getArchitecture())) {
				return download;
			}
		}
		return null;
	}

	/**
	 * Builds the command line used to start the adapter.
	 * @param installDirectory directory where the adapter was installed
	 * @param program program being debugged
	 * @return the command followed by its resolved arguments
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public List<String> commandLine(String installDirectory, String program) {
		return commandLine(installDirectory, program, 0);
	}

	/**
	 * Builds the command line used to start the adapter.
	 * @param installDirectory directory where the adapter was installed
	 * @param program program being debugged
	 * @param port TCP port used by server-style adapters, or {@code 0} when unused
	 * @return the command followed by its resolved arguments
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public List<String> commandLine(String installDirectory, String program, int port) {
		List<String> commandLine = new ArrayList<>();
		commandLine.add(resolve(command, installDirectory, program, port));
		for (String argument : arguments) {
			commandLine.add(resolve(argument, installDirectory, program, port));
		}
		return commandLine;
	}

	/**
	 * Builds the environment variables used to start the adapter.
	 * @param installDirectory directory where the adapter was installed
	 * @param program program being debugged
	 * @param port TCP port used by server-style adapters, or {@code 0} when unused
	 * @return the immutable resolved environment map
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public Map<String, String> environment(String installDirectory, String program, int port) {
		Map<String, String> resolved = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : environment.entrySet()) {
			resolved.put(entry.getKey(), resolve(entry.getValue(), installDirectory, program, port));
		}
		return Map.copyOf(resolved);
	}

	private static String resolve(String value, String installDirectory, String program, int port) {
		return value.replace("${installDir}", installDirectory == null ? "" : installDirectory)
			.replace("${program}", program == null ? "" : program)
			.replace("${port}", Integer.toString(port));
	}

	/**
	 * Returns the stable adapter identifier.
	 * @return the identifier
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the human-readable adapter name.
	 * @return the adapter name
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the adapter version.
	 * @return the version, possibly empty
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Returns the language identifier this adapter debugs.
	 * @return the language identifier, or an empty string when the adapter is not bound
	 * to a language
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public String getLanguageId() {
		return languageId;
	}

	/**
	 * Returns whether the adapter debugs a language.
	 * @param candidate language identifier to compare
	 * @return {@code true} when the adapter is bound to the supplied language
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean supportsLanguage(String candidate) {
		return candidate != null && candidate.equals(languageId);
	}

	/**
	 * Returns the launch command template.
	 * @return the command template
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Returns the launch argument templates.
	 * @return the immutable argument list
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public List<String> getArguments() {
		return arguments;
	}

	/**
	 * Returns every platform download entry.
	 * @return the immutable download list
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public List<DapDownload> getDownloads() {
		return downloads;
	}

	/**
	 * Returns the communication transport used by the adapter.
	 * @return {@code stdio} or {@code tcp}
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public String getTransport() {
		return transport;
	}

	/**
	 * Returns the extra properties merged into the launch request.
	 * @return the immutable launch property map
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public Map<String, String> getLaunchArguments() {
		return launchArguments;
	}

	/**
	 * Resolves the launch properties for a program, expanding the {@code ${program}} and
	 * {@code ${fileDir}} placeholders.
	 * @param program program being debugged
	 * @return the resolved launch properties
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public Map<String, Object> launchArguments(Path program) {
		String programPath = program == null ? "" : program.toString();
		Path parent = program == null ? null : program.getParent();
		String fileDirectory = parent == null ? "." : parent.toString();
		Map<String, Object> resolved = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : launchArguments.entrySet()) {
			resolved.put(entry.getKey(),
					entry.getValue().replace("${program}", programPath).replace("${fileDir}", fileDirectory));
		}
		return resolved;
	}

	/**
	 * Returns the extra environment variables used to start the adapter.
	 * @return the immutable environment map
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public Map<String, String> getEnvironment() {
		return environment;
	}

}
