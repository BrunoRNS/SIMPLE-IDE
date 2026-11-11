package io.simpleide.lsp.dowload;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;

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
    void runsTheOptionalLanguageVersionCommandFromTheRegistry() {
        assertThat(LspManager.getLanguageVersion("java").orElseThrow())
                .containsIgnoringCase("version");
    }

    @Test
    void registryContainsTheRequestedLanguageServers() {
        assertThat(LspManager.getRegistry().getServers())
                .extracting(LspServer::getLanguageId)
                .contains("javascript", "csharp", "c", "cpp", "groovy", "kotlin");
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

        assertThat(LspInstaller.getInstallDirectory(cServer))
                .isEqualTo(LspInstaller.getInstallDirectory(cppServer));
    }

    @Test
    void detectsCSharpAndJsxFiles() throws Exception {
        Path csharp = Files.writeString(home.resolve("Program.cs"), "class Program {}");
        Path jsx = Files.writeString(home.resolve("App.jsx"), "export default App;");

        assertThat(new File(csharp).getTypeFile()).isEqualTo(TypeFile.CSHARP);
        assertThat(new File(jsx).getTypeFile()).isEqualTo(TypeFile.JAVASCRIPT);
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
            LspServer server = new LspServer(
                    "test", "1", "test", "test", List.of(), List.of(),
                    null, null, "java", List.of(
                            "-jar", "./plugins/custom-*.jar",
                            "--config", "./config",
                            "--workspace", "${workspaceData}"));

            List<String> command = LspManager.createCommand(server, home.resolve("project"));

            assertThat(command).containsSubsequence(
                    "-jar", launcher.toAbsolutePath().toString(),
                    "--config", configDirectory.toAbsolutePath().toString());
            assertThat(Path.of(command.get(command.size() - 1))).isAbsolute();
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    @Test
    void removesTheInstallDirectoryWhenInstallationFails() {
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            LspServer server = new LspServer(
                    "test", "1", "test", "test", List.of(), List.of(),
                    null, null, "test-command", List.of());

            assertThat(LspInstaller.dowloading(server)).isFalse();
            assertThat(Files.exists(home.resolve(".simpleide/lsp/test1"))).isFalse();
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }
}
