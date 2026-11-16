package io.simpleide.lsp.dowload;

import io.simpleide.language.config.LangPlatform;
import java.util.List;
import java.util.Map;

/**
 * Registry entry describing how to install, version and launch a language server.
 *
 * <p>
 * An entry declares the supported operating systems and architectures, the minimum
 * language runtime version, the install strategy (command, archiver command or archive
 * URL), the launch command and arguments, and optional release metadata used to discover
 * newer versions. Optional placeholders in commands are resolved by {@link LspManager}
 * against the installation and workspace directories.
 *
 * @author luisotavio15
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public class LspServer {

	private String id;

	private String version;

	private String name;

	private String languageId;

	private String typeFile;

	private String installKey;

	private String archiverCommand;

	private List<String> paramsArchiverCommand;

	private List<String> os;

	private List<String> arch;

	private String minimumVersion;

	private String url;

	private String command;

	private List<String> arguments;

	private String installCommand;

	private List<String> installArguments;

	private String postInstallCommand;

	private List<String> postInstallArguments;

	private String versionCommand;

	private List<String> versionArguments;

	private String latestVersionApi;

	private String latestVersionPath;

	private String latestVersionListPath;

	private String latestAssetName;

	private String latestVersionUrlTemplate;

	private Map<String, String> installEnvironment;

	/**
	 * Creates a fully configured registry entry.
	 * @param id unique registry identifier
	 * @param version version to install, may be refreshed from the release API
	 * @param name human-readable server name
	 * @param languageId LSP language identifier
	 * @param os supported operating systems
	 * @param arch supported architectures
	 * @param minimumVersion minimum language runtime version, or {@code null}
	 * @param url archive download URL, or {@code null}
	 * @param command launch command, or {@code null}
	 * @param arguments launch arguments
	 * @param installCommand package manager install command, or {@code null}
	 * @param installArguments install command arguments
	 * @param typeFile archive extension without the leading dot, or {@code null}
	 * @param versionCommand command that prints the language runtime version, or
	 * {@code null}
	 * @param versionArguments version command arguments
	 * @param paramsArchiverCommand archiver command arguments
	 * @param archiverCommand archiver command, or {@code null}
	 */
	public LspServer(String id, String version, String name, String languageId, List<String> os, List<String> arch,
			String minimumVersion, String url, String command, List<String> arguments, String installCommand,
			List<String> installArguments, String typeFile, String versionCommand, List<String> versionArguments,
			List<String> paramsArchiverCommand, String archiverCommand) {
		this.id = id;
		this.version = version;
		this.name = name;
		this.languageId = languageId;
		this.os = os;
		this.arch = arch;
		this.minimumVersion = minimumVersion;
		this.url = url;
		this.command = command;
		this.arguments = arguments;
		this.installCommand = installCommand;
		this.installArguments = installArguments;
		this.typeFile = typeFile;
		this.archiverCommand = archiverCommand;
		this.paramsArchiverCommand = paramsArchiverCommand;
		this.versionCommand = versionCommand;
		this.versionArguments = versionArguments;
	}

	/**
	 * Creates a minimal registry entry without install or version commands.
	 * @param id unique registry identifier
	 * @param version server version
	 * @param name human-readable server name
	 * @param languageId LSP language identifier
	 * @param os supported operating systems
	 * @param arch supported architectures
	 * @param minimumVersion minimum language runtime version, or {@code null}
	 * @param url archive download URL, or {@code null}
	 * @param command launch command, or {@code null}
	 * @param arguments launch arguments
	 */
	public LspServer(String id, String version, String name, String languageId, List<String> os, List<String> arch,
			String minimumVersion, String url, String command, List<String> arguments) {
		this.id = id;
		this.version = version;
		this.name = name;
		this.languageId = languageId;
		this.os = os;
		this.arch = arch;
		this.minimumVersion = minimumVersion;
		this.url = url;
		this.command = command;
		this.arguments = arguments;
		this.installArguments = List.of();
		this.versionArguments = List.of();
	}

	/**
	 * Tells whether the entry supports the current operating system and architecture.
	 *
	 * <p>
	 * Architecture aliases such as {@code x86_64}/{@code x64} and
	 * {@code arm64}/{@code aarch64} are accepted. An unknown platform is never supported.
	 * @return {@code true} when both the OS and architecture match
	 */
	public boolean isSupportedOnCurrentPlatform() {
		LangPlatform current = LangPlatform.getCurrentPlatform();

		if (current == LangPlatform.UNKNOWN) {
			return false;
		}

		boolean osSupported = os != null
				&& os.stream().anyMatch(supportedOs -> supportedOs.equalsIgnoreCase(current.getOs()));

		boolean archSupported = arch != null
				&& arch.stream().anyMatch(supportedArch -> supportedArch.equalsIgnoreCase(current.getArchitecture()));

		if (!archSupported && arch != null) {
			archSupported = arch.stream().anyMatch(supportedArch -> {
				String normalized = supportedArch.toLowerCase();
				if (current.getArchitecture().equals("x64")) {
					return normalized.contains("x86_64") || normalized.contains("x64");
				}
				if (current.getArchitecture().equals("aarch64")) {
					return normalized.contains("arm64") || normalized.contains("aarch64");
				}
				return false;
			});
		}

		return osSupported && archSupported;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLanguageId() {
		return languageId;
	}

	public void setLanguageId(String languageId) {
		this.languageId = languageId;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public List<String> getArguments() {
		return arguments;
	}

	public void setArguments(List<String> arguments) {
		this.arguments = arguments;
	}

	public List<String> getOs() {
		return os;
	}

	public void setOs(List<String> os) {
		this.os = os;
	}

	public List<String> getArch() {
		return arch;
	}

	public void setArch(List<String> arch) {
		this.arch = arch;
	}

	public String getMinimumVersion() {
		return minimumVersion;
	}

	public void setMinimumVersion(String minimumVersion) {
		this.minimumVersion = minimumVersion;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTypeFile() {
		return typeFile;
	}

	public void setTypeFile(String typeFile) {
		this.typeFile = typeFile;
	}

	public String getInstallKey() {
		return installKey;
	}

	public void setInstallKey(String installKey) {
		this.installKey = installKey;
	}

	public String getInstallCommand() {
		return installCommand;
	}

	public void setInstallCommand(String installCommand) {
		this.installCommand = installCommand;
	}

	public List<String> getInstallArguments() {
		return installArguments;
	}

	public void setInstallArguments(List<String> installArguments) {
		this.installArguments = installArguments;
	}

	public String getPostInstallCommand() {
		return postInstallCommand;
	}

	public void setPostInstallCommand(String postInstallCommand) {
		this.postInstallCommand = postInstallCommand;
	}

	public List<String> getPostInstallArguments() {
		return postInstallArguments;
	}

	public void setPostInstallArguments(List<String> postInstallArguments) {
		this.postInstallArguments = postInstallArguments;
	}

	public String getVersionCommand() {
		return versionCommand;
	}

	public void setVersionCommand(String versionCommand) {
		this.versionCommand = versionCommand;
	}

	public List<String> getVersionArguments() {
		return versionArguments;
	}

	public void setVersionArguments(List<String> versionArguments) {
		this.versionArguments = versionArguments;
	}

	public String getLatestVersionApi() {
		return latestVersionApi;
	}

	public void setLatestVersionApi(String latestVersionApi) {
		this.latestVersionApi = latestVersionApi;
	}

	public String getLatestVersionPath() {
		return latestVersionPath;
	}

	public void setLatestVersionPath(String latestVersionPath) {
		this.latestVersionPath = latestVersionPath;
	}

	public String getLatestVersionListPath() {
		return latestVersionListPath;
	}

	public void setLatestVersionListPath(String latestVersionListPath) {
		this.latestVersionListPath = latestVersionListPath;
	}

	public String getLatestAssetName() {
		return latestAssetName;
	}

	public void setLatestAssetName(String latestAssetName) {
		this.latestAssetName = latestAssetName;
	}

	public String getLatestVersionUrlTemplate() {
		return latestVersionUrlTemplate;
	}

	public void setLatestVersionUrlTemplate(String latestVersionUrlTemplate) {
		this.latestVersionUrlTemplate = latestVersionUrlTemplate;
	}

	public Map<String, String> getInstallEnvironment() {
		return installEnvironment;
	}

	public void setInstallEnvironment(Map<String, String> installEnvironment) {
		this.installEnvironment = installEnvironment;
	}

	public String getArchiverCommand() {
		return archiverCommand;
	}

	public void setArchiverCommand(String archiverCommand) {
		this.archiverCommand = archiverCommand;
	}

	public List<String> getParamsArchiverCommand() {
		return paramsArchiverCommand;
	}

	public void setParamsArchiverCommand(List<String> paramsArchiverCommand) {
		this.paramsArchiverCommand = paramsArchiverCommand;
	}

}
