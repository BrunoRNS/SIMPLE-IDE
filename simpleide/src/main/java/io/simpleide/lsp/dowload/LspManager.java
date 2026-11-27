package io.simpleide.lsp.dowload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.lsp.LspStatus;
import io.simpleide.utils.JavaRuntime;

/**
 * Reads the language server registry and resolves launch commands.
 *
 * <p>
 * The manager filters registry entries by language and host platform, selects the newest
 * server compatible with the detected language runtime, refreshes release metadata and
 * ensures the server is installed before building its command. Command arguments support
 * placeholders for the install directory, workspace data, version and platform
 * configuration. The package name keeps the original misspelling {@code dowload}.
 *
 * <p>
 * Covered by {@code LspManagerTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LSP.md">docs/reference/LSP.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class LspManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(LspManager.class);

	private static final String RUST_PROJECT_FILE = "rust-project.json";

	private static final AtomicBoolean RUST_SOURCES_WARNING = new AtomicBoolean();

	/**
	 * Returns the launch command for a language without a workspace root or status
	 * listener.
	 * @param language LSP language identifier
	 * @return the command and arguments, or an empty list when no server matches
	 * @throws IllegalStateException when the matched server cannot be installed
	 */
	public static List<String> getComandRun(String language) {
		return getComandRun(language, null);
	}

	/**
	 * Returns the launch command for a language and workspace root.
	 * @param language LSP language identifier
	 * @param workspaceRoot workspace root passed to the server, may be {@code null}
	 * @return the command and arguments, or an empty list when no server matches
	 * @throws IllegalStateException when the matched server cannot be installed
	 */
	public static List<String> getComandRun(String language, Path workspaceRoot) {
		return getComandRun(language, workspaceRoot, language, ignored -> {
		});
	}

	/**
	 * Returns the launch command, installing and reporting status when needed.
	 * @param language LSP language identifier
	 * @param workspaceRoot workspace root passed to the server, may be {@code null}
	 * @param statusId identifier included in status events
	 * @param statusListener consumer for installation status events
	 * @return the command and arguments, or an empty list when no server matches
	 * @throws IllegalStateException when the matched server cannot be installed
	 */
	public static List<String> getComandRun(String language, Path workspaceRoot, String statusId,
			Consumer<LspStatus> statusListener) {
		List<LspServer> candidates = getRegistry().getServers()
			.stream()
			.filter(server -> server.getLanguageId().equals(language) && server.isSupportedOnCurrentPlatform())
			.toList();
		LspServer server = selectServer(candidates);
		if (server == null) {
			publishStatus(statusListener, statusId, language, LspStatus.State.IDLE);
			return new ArrayList<>();
		}

		publishStatus(statusListener, statusId, server.getName(), LspStatus.State.CHECKING_VERSION);
		String detectedVersion = LspVersionChecker.checkMinimumVersion(server);
		LspReleaseResolver.refresh(server);
		if (LspInstaller.isInstalled(server)) {
			return createCommand(server, workspaceRoot);
		}
		if (LspInstaller.dowloading(server, statusId, statusListener)) {
			return createCommand(server, workspaceRoot);
		}
		throw new IllegalStateException("Unable to install language server " + server.getName()
				+ (detectedVersion == null ? "" : " (language version " + detectedVersion + ")"));
	}

	/**
	 * Returns the launch command for an older server compatible with the runtime.
	 *
	 * <p>
	 * This is used after the primary server exits because the local language runtime is
	 * too old. The fallback must be supported on the platform, have a lower minimum
	 * version than the primary server and still be compatible with the detected version.
	 * @param language LSP language identifier
	 * @param workspaceRoot workspace root passed to the server, may be {@code null}
	 * @param statusId identifier included in status events
	 * @param statusListener consumer for installation status events
	 * @return the fallback command and arguments, or an empty list when none is available
	 */
	public static List<String> getFallbackCommandRun(String language, Path workspaceRoot, String statusId,
			Consumer<LspStatus> statusListener) {
		List<LspServer> candidates = getRegistry().getServers()
			.stream()
			.filter(server -> server.getLanguageId().equals(language) && server.isSupportedOnCurrentPlatform())
			.toList();
		LspServer primary = selectServer(candidates);
		if (primary == null) {
			return new ArrayList<>();
		}

		LspServer versionProbe = candidates.stream()
			.filter(server -> server.getMinimumVersion() != null && !server.getMinimumVersion().isBlank())
			.filter(server -> server.getVersionCommand() != null && !server.getVersionCommand().isBlank())
			.findFirst()
			.orElse(null);
		if (versionProbe == null) {
			return new ArrayList<>();
		}

		LspServer fallback = selectFallbackServer(candidates, LspVersionChecker.runVersionCommand(versionProbe),
				primary);
		if (fallback == null) {
			return new ArrayList<>();
		}

		publishStatus(statusListener, statusId, fallback.getName(), LspStatus.State.CHECKING_VERSION);
		if (LspInstaller.isInstalled(fallback) || LspInstaller.dowloading(fallback, statusId, statusListener)) {
			LOGGER.info("Falling back to language server {} {} for {}.", fallback.getName(), fallback.getVersion(),
					language);
			return createCommand(fallback, workspaceRoot);
		}
		return new ArrayList<>();
	}

	static LspServer selectFallbackServer(List<LspServer> candidates, String detectedVersion, LspServer primary) {
		if (primary == null || detectedVersion == null || detectedVersion.isBlank()
				|| primary.getMinimumVersion() == null || primary.getMinimumVersion().isBlank()) {
			return null;
		}

		return candidates.stream()
			.filter(candidate -> candidate != primary)
			.filter(candidate -> candidate.getMinimumVersion() != null && !candidate.getMinimumVersion().isBlank())
			.filter(candidate -> compareVersions(candidate.getMinimumVersion(), primary.getMinimumVersion()) < 0)
			.filter(candidate -> compareVersions(detectedVersion, candidate.getMinimumVersion()) >= 0)
			.max((first, second) -> compareVersions(first.getMinimumVersion(), second.getMinimumVersion()))
			.orElse(null);
	}

	private static LspServer selectServer(List<LspServer> candidates) {
		List<LspServer> versionedCandidates = candidates.stream()
			.filter(server -> server.getMinimumVersion() != null && !server.getMinimumVersion().isBlank())
			.toList();
		if (versionedCandidates.size() < 2) {
			return candidates.isEmpty() ? null : candidates.get(0);
		}

		LspServer versionProbe = versionedCandidates.stream()
			.filter(server -> server.getVersionCommand() != null && !server.getVersionCommand().isBlank())
			.findFirst()
			.orElse(null);
		if (versionProbe == null) {
			return candidates.get(0);
		}

		return selectClosestCompatibleServer(versionedCandidates, LspVersionChecker.runVersionCommand(versionProbe));
	}

	static LspServer selectClosestCompatibleServer(List<LspServer> candidates, String detectedVersion) {
		LspServer selected = null;
		for (LspServer candidate : candidates) {
			if (compareVersions(detectedVersion, candidate.getMinimumVersion()) < 0) {
				continue;
			}
			if (selected == null || compareVersions(candidate.getMinimumVersion(), selected.getMinimumVersion()) > 0) {
				selected = candidate;
			}
		}
		if (selected != null) {
			return selected;
		}
		return candidates.stream()
			.min((first, second) -> compareVersions(first.getMinimumVersion(), second.getMinimumVersion()))
			.orElseThrow();
	}

	private static void publishStatus(Consumer<LspStatus> statusListener, String id, String serverName,
			LspStatus.State state) {
		if (statusListener != null) {
			statusListener.accept(new LspStatus(id, serverName, state));
		}
	}

	/**
	 * Detects the installed language runtime version for a registry entry.
	 * @param language LSP language identifier
	 * @return the version output, or an empty optional when no version command is
	 * configured
	 */
	public static Optional<String> getLanguageVersion(String language) {
		for (LspServer server : getRegistry().getServers()) {
			if (!server.getLanguageId().equals(language) || !server.isSupportedOnCurrentPlatform()
					|| server.getVersionCommand() == null || server.getVersionCommand().isBlank()) {
				continue;
			}
			return Optional.of(LspVersionChecker.runVersionCommand(server));
		}
		return Optional.empty();
	}

	/**
	 * Returns the extra initialization options a language server needs.
	 *
	 * <p>
	 * rust-analyzer only discovers Cargo projects, so a standalone {@code .rs} file
	 * outside one initializes without offering completion. When the workspace has no
	 * {@code Cargo.toml} or {@code rust-project.json}, the loose {@code .rs} files are
	 * passed as {@code linkedProjects}; rust-analyzer treats each of them as a standalone
	 * file and resolves the standard library from the toolchain.
	 * @param language LSP language identifier
	 * @param workspaceRoot workspace root passed to the server, may be {@code null}
	 * @return initialization options, empty when the server needs none
	 */
	public static Map<String, Object> getInitializationOptions(String language, Path workspaceRoot) {
		if (!"rust".equals(language) || workspaceRoot == null) {
			return Map.of();
		}
		Path root = workspaceRoot.toAbsolutePath().normalize();
		if (Files.exists(root.resolve("Cargo.toml")) || Files.isRegularFile(root.resolve(RUST_PROJECT_FILE))) {
			return Map.of();
		}

		List<String> sources;
		try (var paths = Files.list(root)) {
			sources = paths.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".rs"))
				.map(path -> path.toAbsolutePath().normalize().toString())
				.sorted()
				.toList();
		}
		catch (IOException error) {
			LOGGER.warn("Unable to list the standalone Rust files in {}.", root, error);
			return Map.of();
		}
		if (sources.isEmpty()) {
			return Map.of();
		}
		warnWhenRustSourcesAreMissing();
		return Map.of("linkedProjects", sources);
	}

	private static void warnWhenRustSourcesAreMissing() {
		if (!RUST_SOURCES_WARNING.compareAndSet(false, true)) {
			return;
		}
		Path sysroot = rustcSysroot();
		if (sysroot != null && Files
			.isDirectory(sysroot.resolve("lib").resolve("rustlib").resolve("src").resolve("rust").resolve("library"))) {
			return;
		}
		LOGGER.warn("The rust-src component is missing; Rust completions for standalone files will be limited. "
				+ "Install it with 'rustup component add rust-src'.");
	}

	private static Path rustcSysroot() {
		Path outputFile;
		try {
			outputFile = Files.createTempFile("simpleide-rustc-sysroot-", ".log");
		}
		catch (IOException error) {
			return null;
		}
		Process process = null;
		try {
			process = new ProcessBuilder("rustc", "--print", "sysroot").redirectErrorStream(true)
				.redirectOutput(outputFile.toFile())
				.start();
			if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
				return null;
			}
			String output = Files.readString(outputFile, StandardCharsets.UTF_8).trim();
			return output.isBlank() ? null : Paths.get(output);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return null;
		}
		catch (IOException error) {
			return null;
		}
		finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
			try {
				Files.deleteIfExists(outputFile);
			}
			catch (IOException error) {
				outputFile.toFile().deleteOnExit();
			}
		}
	}

	private static Path workspaceDataDirectory(Path workspaceRoot) {
		String workspaceKey = workspaceRoot == null ? "default" : workspaceRoot.toAbsolutePath().normalize().toString();
		return Paths.get(System.getProperty("user.home"), ".cache", "simpleide", "lsp",
				UUID.nameUUIDFromBytes(workspaceKey.getBytes(StandardCharsets.UTF_8)).toString());
	}

	static List<String> createCommand(LspServer server, Path workspaceRoot) {
		if (server.getCommand() == null || server.getCommand().isBlank()) {
			throw new IllegalStateException("No launch command configured for " + server.getName());
		}
		Path installDirectory = LspInstaller.getInstallDirectory(server);
		String workspacePath = workspaceRoot == null ? "" : workspaceRoot.toAbsolutePath().normalize().toString();
		Path workspaceData = workspaceDataDirectory(workspaceRoot);
		if ("csharp".equals(server.getLanguageId())) {
			CSharpWorkspaceProject.createIfNeeded(workspaceRoot);
		}
		String platformConfiguration = switch (LangPlatform.getCurrentPlatform().getOs()) {
			case "windows" -> "config_win";
			case "macos" -> "config_mac";
			case "linux" -> "config_linux";
			default -> throw new IllegalStateException("Unsupported platform for language server.");
		};

		List<String> command = new ArrayList<>();
		command.addAll(resolveArgument(resolveJavaCommand(server.getCommand()), installDirectory, workspaceData,
				workspacePath, server.getVersion(), platformConfiguration));
		if (server.getArguments() != null) {
			for (String argument : server.getArguments()) {
				command.addAll(resolveArgument(argument, installDirectory, workspaceData, workspacePath,
						server.getVersion(), platformConfiguration));
			}
		}
		return command;
	}

	private static String resolveJavaCommand(String command) {
		if (!JavaRuntime.isJavaCommand(command)) {
			return command;
		}
		return JavaRuntime.javaExecutable().map(Path::toString).orElse(command);
	}

	private static List<String> resolveArgument(String argument, Path installDirectory, Path workspaceData,
			String workspacePath, String version, String platformConfiguration) {
		if (argument.contains("${workspaceData}")) {
			try {
				Files.createDirectories(workspaceData);
			}
			catch (IOException e) {
				throw new IllegalStateException("Unable to create LSP workspace data directory.", e);
			}
		}
		String resolved = argument.replace("${installDir}", installDirectory.toAbsolutePath().toString())
			.replace("${workspaceData}", workspaceData.toString())
			.replace("${workspaceRoot}", workspacePath)
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
		if (containsGlob(resolved) && (path.isAbsolute() || resolved.contains("/") || resolved.contains("\\"))) {
			return expandGlob(path, installDirectory, globMarker);
		}
		if (path.isAbsolute()) {
			return List.of(path.normalize().toString());
		}

		Path installedPath = installDirectory.resolve(path).normalize();
		if (installedPath.startsWith(installDirectory.toAbsolutePath().normalize()) && Files.exists(installedPath)) {
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
		}
		while (pattern.contains(marker));
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
			List<String> matches = paths.filter(path -> matcher.matches(root.relativize(path)))
				.sorted()
				.map(path -> path.toAbsolutePath().toString())
				.toList();
			if (matches.isEmpty()) {
				throw new IllegalStateException("LSP command pattern did not match any installed file: " + pattern);
			}
			return matches;
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to expand LSP command pattern: " + pattern, e);
		}
	}

	static int compareVersions(String actual, String minimum) {
		return LspVersionChecker.compareVersions(actual, minimum);
	}

	/**
	 * Loads the bundled language server registry from the classpath.
	 * @return the parsed registry
	 * @throws RuntimeException when the registry resource is missing or malformed
	 */
	public static LspRegistry getRegistry() {
		return LspRegistryLoader.load();
	}

}
