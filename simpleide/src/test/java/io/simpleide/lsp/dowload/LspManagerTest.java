package io.simpleide.lsp.dowload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import io.simpleide.ui.treefile.SimpleIDEFile;
import io.simpleide.ui.treefile.TypeFile;

/**
 * Tests {@link io.simpleide.lsp.dowload.LspManager}.
 */
class LspManagerTest {

	@TempDir
	Path home;

	@Test
	void acceptsVersionsEqualToTheMinimum() {
		assertThat(LspManager.compareVersions("OpenJDK 17", "17")).isZero();
	}

	@Test
	void rejectsVersionsBelowTheMinimum() {
		assertThat(LspManager.compareVersions("java version 11.0.22", "17")).isNegative();
	}

	@Test
	void comparesVersionComponentsNumerically() {
		assertThat(LspManager.compareVersions("Python 3.10.1", "3.9")).isPositive();
	}

	@Test
	void selectsTheHighestLanguageMinimumSupportedByTheDetectedVersion() {
		LspServer java8 = javaServerWithMinimumVersion("java-8", "8.0.0");
		LspServer java17 = javaServerWithMinimumVersion("java-17", "17.0.0");
		LspServer java21 = javaServerWithMinimumVersion("java-21", "21.0.0");

		assertThat(LspManager.selectClosestCompatibleServer(List.of(java8, java21, java17), "openjdk version 21.0.0"))
			.isSameAs(java21);
	}

	@Test
	void selectsTheClosestSupportedMinimumWhenDetectedVersionFallsBetweenServers() {
		LspServer java8 = javaServerWithMinimumVersion("java-8", "8.0.0");
		LspServer java17 = javaServerWithMinimumVersion("java-17", "17.0.0");
		LspServer java21 = javaServerWithMinimumVersion("java-21", "21.0.0");

		assertThat(LspManager.selectClosestCompatibleServer(List.of(java8, java17, java21), "openjdk version 19.0.0"))
			.isSameAs(java17);
	}

	@Test
	void runsTheOptionalLanguageVersionCommandFromTheRegistry() {
		boolean javaVersionCommandSupported = LspManager.getRegistry()
			.getServers()
			.stream()
			.anyMatch(server -> server.getLanguageId().equals("java") && server.isSupportedOnCurrentPlatform()
					&& server.getVersionCommand() != null && !server.getVersionCommand().isBlank());

		if (javaVersionCommandSupported) {
			assertThat(LspManager.getLanguageVersion("java").orElseThrow()).containsIgnoringCase("version");
		}
		else {
			assertThat(LspManager.getLanguageVersion("java")).isEmpty();
		}
	}

	@Test
	void returnsEmptyVersionWhenNoJavaServerSupportsTheCurrentPlatform() {
		String originalOsName = System.getProperty("os.name");
		String originalOsArch = System.getProperty("os.arch");
		try {
			System.setProperty("os.name", "Plan9");
			System.setProperty("os.arch", "x86");

			assertThat(LspManager.getLanguageVersion("java")).isEmpty();
		}
		finally {
			System.setProperty("os.name", originalOsName);
			System.setProperty("os.arch", originalOsArch);
		}
	}

	@Test
	void registryContainsTheRequestedLanguageServers() {
		assertThat(LspManager.getRegistry().getServers()).extracting(LspServer::getLanguageId)
			.contains("java", "javascript", "csharp", "c", "cpp", "groovy", "kotlin", "python", "go", "rust");

		LspServer goServer = LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getLanguageId().equals("go"))
			.findFirst()
			.orElseThrow();
		assertThat(goServer.getLatestVersionApi()).isEqualTo("https://proxy.golang.org/golang.org/x/tools/@latest");
		assertThat(goServer.getInstallEnvironment()).containsEntry("GOBIN", "${installDir}");
	}

	@Test
	void providesJdtlsVersionsForJava17AndJava25On64BitPlatforms() {
		List<LspServer> jdtlsServers = LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getName().equals("jdtls"))
			.toList();

		assertThat(jdtlsServers).extracting(LspServer::getVersion).containsExactly("1.43.0", "1.63.0");
		assertThat(jdtlsServers).extracting(LspServer::getMinimumVersion).containsExactly("17", "25");
		assertThat(jdtlsServers).allSatisfy(server -> assertThat(server.getArch()).doesNotContain("x86"));
	}

	@Test
	void configuresOmniSharpOnMonoForLooseCSharpFiles() throws Exception {
		LspServer csharp = LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getLanguageId().equals("csharp"))
			.findFirst()
			.orElseThrow();

		assertThat(csharp.getId()).isEqualTo("omnisharp-mono");
		assertThat(csharp.getVersionCommand()).isEqualTo("mono");
		assertThat(csharp.getMinimumVersion()).isEqualTo("6");
		assertThat(csharp.getUrl()).endsWith("omnisharp-mono.tar.gz");
		assertThat(LspManager.createCommand(csharp, home)).containsSubsequence("mono", "--languageserver", "--stdio",
				"-s", home.toAbsolutePath().normalize().toString());
	}

	@Test
	void selectsThePinnedGoplsReleaseForOlderGoToolchains() {
		List<LspServer> goplsServers = LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getLanguageId().equals("go"))
			.filter(server -> server.getOs().contains("LINUX"))
			.toList();

		assertThat(LspManager.selectClosestCompatibleServer(goplsServers, "go version go1.22.2 linux/amd64"))
			.extracting(LspServer::getId)
			.isEqualTo("go-gopls-legacy");
		assertThat(LspManager.selectClosestCompatibleServer(goplsServers, "go version go1.26.1 linux/amd64"))
			.extracting(LspServer::getId)
			.isEqualTo("go-gopls");
	}

	@Test
	void selectsTheJava17CompatibleJdtlsReleaseOnJava21() {
		String originalOsName = System.getProperty("os.name");
		String originalOsArch = System.getProperty("os.arch");
		try {
			System.setProperty("os.name", "Linux");
			System.setProperty("os.arch", "amd64");

			List<LspServer> jdtlsServers = LspManager.getRegistry()
				.getServers()
				.stream()
				.filter(server -> server.getName().equals("jdtls"))
				.filter(LspServer::isSupportedOnCurrentPlatform)
				.toList();

			assertThat(LspManager.selectClosestCompatibleServer(jdtlsServers, "openjdk version 21.0.1"))
				.extracting(LspServer::getId)
				.isEqualTo("jdtls-java17");
		}
		finally {
			System.setProperty("os.name", originalOsName);
			System.setProperty("os.arch", originalOsArch);
		}
	}

	@Test
	void selectsTheJava25JdtlsReleaseOnJava25() {
		String originalOsName = System.getProperty("os.name");
		String originalOsArch = System.getProperty("os.arch");
		try {
			System.setProperty("os.name", "Linux");
			System.setProperty("os.arch", "amd64");

			List<LspServer> jdtlsServers = LspManager.getRegistry()
				.getServers()
				.stream()
				.filter(server -> server.getName().equals("jdtls"))
				.filter(LspServer::isSupportedOnCurrentPlatform)
				.toList();

			assertThat(LspManager.selectClosestCompatibleServer(jdtlsServers, "openjdk version 25.0.1"))
				.extracting(LspServer::getId)
				.isEqualTo("jdtls-java25");
		}
		finally {
			System.setProperty("os.name", originalOsName);
			System.setProperty("os.arch", originalOsArch);
		}
	}

	@Test
	void selectsTheJava17JdtlsReleaseAsFallbackWhenTheJava25ReleaseFails() {
		List<LspServer> jdtlsServers = LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getName().equals("jdtls"))
			.toList();
		LspServer primary = LspManager.selectClosestCompatibleServer(jdtlsServers, "openjdk version 25.0.1");

		assertThat(primary).extracting(LspServer::getId).isEqualTo("jdtls-java25");
		assertThat(LspManager.selectFallbackServer(jdtlsServers, "openjdk version 25.0.1", primary))
			.extracting(LspServer::getId)
			.isEqualTo("jdtls-java17");
	}

	@Test
	void doesNotSelectAFallbackWhenTheSelectedJdtlsReleaseIsAlreadyTheOldest() {
		List<LspServer> jdtlsServers = LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getName().equals("jdtls"))
			.toList();
		LspServer primary = LspManager.selectClosestCompatibleServer(jdtlsServers, "openjdk version 21.0.1");

		assertThat(primary).extracting(LspServer::getId).isEqualTo("jdtls-java17");
		assertThat(LspManager.selectFallbackServer(jdtlsServers, "openjdk version 21.0.1", primary)).isNull();
	}

	@Test
	void configuresRustAnalyzerForWindowsX86() {
		LspServer rustServer = LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getId().equals("rust-analyzer-windows-x86"))
			.findFirst()
			.orElseThrow();

		assertThat(rustServer.getArch()).containsExactly("x86");
		assertThat(rustServer.getOs()).containsExactly("WINDOWS");
		assertThat(rustServer.getLatestAssetName()).isEqualTo("rust-analyzer-i686-pc-windows-msvc.zip");
	}

	@Test
	void cAndCppReuseOneClangdInstallation() {
		List<LspServer> servers = LspManager.getRegistry().getServers();
		LspServer cServer = servers.stream()
			.filter(server -> server.getLanguageId().equals("c"))
			.findFirst()
			.orElseThrow();
		LspServer cppServer = servers.stream()
			.filter(server -> server.getLanguageId().equals("cpp"))
			.findFirst()
			.orElseThrow();

		assertThat(LspInstaller.getInstallDirectory(cServer)).isEqualTo(LspInstaller.getInstallDirectory(cppServer));
	}

	@Test
	void limitsX86ServersToVerifiedRuntimeAndReleaseCombinations() {
		String originalOsName = System.getProperty("os.name");
		String originalOsArch = System.getProperty("os.arch");
		try {
			System.setProperty("os.name", "Windows 11");
			System.setProperty("os.arch", "x86");

			assertThat(LspManager.getRegistry()
				.getServers()
				.stream()
				.filter(LspServer::isSupportedOnCurrentPlatform)
				.map(LspServer::getId)).containsExactly("omnisharp-mono", "kotlin-language-server-windows",
						"go-gopls-windows", "go-gopls-legacy-windows", "rust-analyzer-windows-x86");

			assertThat(LspManager.getRegistry().getServers())
				.filteredOn(server -> List.of("java", "javascript", "python").contains(server.getLanguageId()))
				.noneMatch(LspServer::isSupportedOnCurrentPlatform);

			assertThat(LspManager.getRegistry()
				.getServers()
				.stream()
				.filter(server -> server.getLanguageId().equals("csharp"))
				.filter(LspServer::isSupportedOnCurrentPlatform)).extracting(LspServer::getId)
				.containsExactly("omnisharp-mono");

			System.setProperty("os.name", "Linux");
			assertThat(LspManager.getRegistry()
				.getServers()
				.stream()
				.filter(LspServer::isSupportedOnCurrentPlatform)
				.map(LspServer::getId))
				.containsExactly("omnisharp-mono", "kotlin-language-server", "go-gopls", "go-gopls-legacy");
		}
		finally {
			System.setProperty("os.name", originalOsName);
			System.setProperty("os.arch", originalOsArch);
		}
	}

	@Test
	void detectsCSharpAndJsxFiles() throws Exception {
		Path csharp = Files.writeString(home.resolve("Program.cs"), "class Program {}");
		Path jsx = Files.writeString(home.resolve("App.jsx"), "export default App;");
		Path go = Files.writeString(home.resolve("main.go"), "package main");

		assertThat(new SimpleIDEFile(csharp).getTypeFile()).isEqualTo(TypeFile.CSHARP);
		assertThat(new SimpleIDEFile(jsx).getTypeFile()).isEqualTo(TypeFile.JAVASCRIPT);
		assertThat(new SimpleIDEFile(go).getTypeFile()).isEqualTo(TypeFile.GO);
	}

	@Test
	void requiresTheInstalledMarkerToMatchTheSelectedServerVersion() throws Exception {
		String originalHome = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			LspServer server = new LspServer("test", "1.2.0", "test", "test", List.of(), List.of(), null, null,
					"test-command", List.of());
			Path installDirectory = LspInstaller.getInstallDirectory(server);
			Files.createDirectories(installDirectory);
			Path marker = Files.writeString(installDirectory.resolve(".installed"), "1.1.0");

			assertThat(LspInstaller.isInstalled(server)).isFalse();

			Files.writeString(marker, "1.2.0");
			assertThat(LspInstaller.isInstalled(server)).isTrue();
		}
		finally {
			if (originalHome == null) {
				System.clearProperty("user.home");
			}
			else {
				System.setProperty("user.home", originalHome);
			}
		}
	}

	@Test
	void resolvesWildcardAndRelativePathsInsideAnyServerInstallation() throws Exception {
		String originalHome = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			Path installDirectory = Files.createDirectories(home.resolve(".simpleide/lsp/test1"));
			Path pluginsDirectory = Files.createDirectories(installDirectory.resolve("plugins"));
			Path launcher = Files.createFile(pluginsDirectory.resolve("custom-server-launcher.jar"));
			Path configDirectory = Files.createDirectories(installDirectory.resolve("config"));
			LspServer server = new LspServer("test", "1", "test", "test", List.of(), List.of(), null, null, "java",
					List.of("-jar", "./plugins/custom-*.jar", "--config", "./config", "--workspace", "${workspaceData}",
							"--version", "${version}"));

			List<String> command = LspManager.createCommand(server, home.resolve("project"));

			assertThat(command).containsSubsequence("-jar", launcher.toAbsolutePath().toString(), "--config",
					configDirectory.toAbsolutePath().toString(), "--version", "1");
			assertThat(Path.of(command.get(command.indexOf("--workspace") + 1))).isAbsolute();
		}
		finally {
			if (originalHome == null) {
				System.clearProperty("user.home");
			}
			else {
				System.setProperty("user.home", originalHome);
			}
		}
	}

	@Test
	void removesTheInstallDirectoryWhenInstallationFails() {
		String originalHome = System.getProperty("user.home");
		Logger installerLogger = (Logger) LoggerFactory.getLogger(LspInstaller.class);
		Level originalLogLevel = installerLogger.getLevel();
		System.setProperty("user.home", home.toString());
		installerLogger.setLevel(Level.OFF);
		try {
			LspServer server = new LspServer("test", "1", "test", "test", List.of(), List.of(), null, null,
					"test-command", List.of());

			assertThat(LspInstaller.dowloading(server)).isFalse();
			assertThat(Files.exists(home.resolve(".simpleide/lsp/test1"))).isFalse();
		}
		finally {
			installerLogger.setLevel(originalLogLevel);
			if (originalHome == null) {
				System.clearProperty("user.home");
			}
			else {
				System.setProperty("user.home", originalHome);
			}
		}
	}

	@Test
	void linksLooseRustFilesWhenTheWorkspaceHasNoCargoProject() throws Exception {
		Path workspace = Files.createDirectories(home.resolve("scripts"));
		Path first = Files.writeString(workspace.resolve("main.rs"), "fn main() {}");
		Path second = Files.writeString(workspace.resolve("other.rs"), "fn other() {}");
		Files.writeString(workspace.resolve("notes.txt"), "ignored");

		assertThat(LspManager.getInitializationOptions("rust", workspace)).containsEntry("linkedProjects",
				List.of(first.toAbsolutePath().normalize().toString(), second.toAbsolutePath().normalize().toString()));
	}

	@Test
	void keepsRustAnalyzerDiscoveryForCargoProjects() throws Exception {
		Path workspace = Files.createDirectories(home.resolve("crate"));
		Files.writeString(workspace.resolve("Cargo.toml"), "[package]\nname = \"demo\"\nversion = \"0.1.0\"\n");
		Files.writeString(workspace.resolve("main.rs"), "fn main() {}");

		assertThat(LspManager.getInitializationOptions("rust", workspace)).isEmpty();
	}

	@Test
	void writesAProjectForLooseCSharpFiles() throws Exception {
		Path workspace = Files.createDirectories(home.resolve("cs-scripts"));
		Files.writeString(workspace.resolve("Program.cs"), "class Program {}\n");
		Path other = Files.writeString(workspace.resolve("Extra.cs"), "class Extra {}\n");
		LspServer csharp = csharpServer();

		LspManager.createCommand(csharp, workspace);

		Path project = workspace.resolve("SimpleIdeLooseFiles.csproj");
		assertThat(project).exists();
		assertThat(Files.readString(project))
			.contains("<Compile Include=\"Extra.cs\" />", "<Compile Include=\"Program.cs\" />")
			.doesNotContain(other.toAbsolutePath().toString());
	}

	@Test
	void keepsAnExistingCSharpProject() throws Exception {
		Path workspace = Files.createDirectories(home.resolve("cs-app"));
		Files.writeString(workspace.resolve("Program.cs"), "class Program {}\n");
		Files.writeString(workspace.resolve("App.csproj"), "<Project />\n");
		LspServer csharp = csharpServer();

		List<String> command = LspManager.createCommand(csharp, workspace);

		assertThat(Files.exists(workspace.resolve("SimpleIdeLooseFiles.csproj"))).isFalse();
		assertThat(command.get(command.size() - 1)).isEqualTo(workspace.toAbsolutePath().normalize().toString());
	}

	private static LspServer csharpServer() {
		return LspManager.getRegistry()
			.getServers()
			.stream()
			.filter(server -> server.getLanguageId().equals("csharp"))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void returnsNoInitializationOptionsForOtherLanguages() {
		assertThat(LspManager.getInitializationOptions("java", home)).isEmpty();
	}

	private static LspServer javaServerWithMinimumVersion(String id, String minimumVersion) {
		LspServer server = new LspServer(id, "1", id, "java", List.of(), List.of(), minimumVersion, null, "java",
				List.of());
		server.setVersionCommand("java");
		server.setVersionArguments(List.of("-version"));
		return server;
	}

}
