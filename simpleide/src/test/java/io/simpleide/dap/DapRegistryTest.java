package io.simpleide.dap;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.simpleide.language.config.LangPlatform;

/**
 * Tests {@link io.simpleide.dap.DapRegistry}, {@link DapAdapter} and {@link DapDownload}.
 */
class DapRegistryTest {

	private static final String REGISTRY = """
			{
			  "adapters": [
			    {
			      "id": "test-adapter",
			      "name": "Test Adapter",
			      "version": "1.2.3",
			      "command": "node",
			      "arguments": ["${installDir}/adapter.js", "${port}"],
			      "launch": {"type": "node"},
			      "environment": {"ADAPTER_HOME": "${installDir}"},
			      "downloads": [
			        {"os": "linux", "arch": "x64", "url": "https://example.com/linux.tar.gz",
			         "archiveType": "tar.gz"},
			        {"os": "macos", "arch": "arm64", "url": "https://example.com/mac.tar.gz",
			         "archiveType": "tar.gz"}
			      ]
			    }
			  ]
			}
			""";

	@Test
	void parsesAdaptersAndResolvesTheCurrentPlatformDownload() {
		DapRegistry registry = DapRegistry.parse(REGISTRY);

		assertThat(registry.getAdapters()).hasSize(1);
		DapAdapter adapter = registry.findForPlatform(LangPlatform.LINUXX64);
		assertThat(adapter).isNotNull();
		assertThat(adapter.getId()).isEqualTo("test-adapter");
		assertThat(adapter.getLaunchArguments()).containsEntry("type", "node");
		assertThat(adapter.environment("/opt/adapters", "/tmp/program.js", 0)).containsEntry("ADAPTER_HOME",
				"/opt/adapters");
		assertThat(adapter.downloadFor(LangPlatform.LINUXX64)).isNotNull();
		assertThat(adapter.downloadFor(LangPlatform.MACOSAARCH64)).isNotNull();
		assertThat(registry.supports(LangPlatform.WINDOWSX64UNICODE)).isFalse();
	}

	@Test
	void keepsBooleanAndNumericLaunchOptionsAsNativeJsonTypes() {
		String registryJson = """
				{
				  "adapters": [
				    {
				      "id": "python-like",
				      "name": "Python-like",
				      "command": "python3",
				      "launch": {
				        "console": "integratedTerminal",
				        "justMyCode": false,
				        "trace": true,
				        "retries": 2,
				        "program": "${program}",
				        "cwd": "${fileDir}"
				      },
				      "downloads": [
				        {"os": "linux", "arch": "x64", "url": "https://example.com/a.zip"}
				      ]
				    }
				  ]
				}
				""";
		DapAdapter adapter = DapRegistry.parse(registryJson).getAdapters().get(0);

		assertThat(adapter.getLaunchArguments()).containsEntry("justMyCode", Boolean.FALSE)
			.containsEntry("trace", Boolean.TRUE)
			.containsEntry("retries", 2);
		assertThat(adapter.launchArguments(Path.of("/tmp/main.py"))).containsEntry("program", "/tmp/main.py")
			.containsEntry("cwd", "/tmp")
			.containsEntry("justMyCode", Boolean.FALSE);
	}

	@Test
	void rejectsUnsupportedLaunchPropertyTypes() {
		String registryJson = """
				{
				  "adapters": [
				    {
				      "id": "broken",
				      "name": "Broken",
				      "command": "node",
				      "launch": {"args": ["-x"]},
				      "downloads": [
				        {"os": "linux", "arch": "x64", "url": "https://example.com/a.zip"}
				      ]
				    }
				  ]
				}
				""";

		assertThatThrownBy(() -> DapRegistry.parse(registryJson)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("launch properties");
	}

	@Test
	void resolvesArchitectureAliases() {
		DapDownload amd64 = new DapDownload("linux", "amd64", "https://example.com/adapter", null);

		assertThat(amd64.matches("linux", "x64")).isTrue();
		assertThat(amd64.matches("linux", "aarch64")).isFalse();
		DapDownload appleSilicon = new DapDownload("macos", "aarch64", "https://example.com/adapter", null);
		assertThat(appleSilicon.matches("macos", "arm64")).isTrue();
	}

	@Test
	void buildsACommandLineWithInstallDirectoryAndPortPlaceholders() {
		DapAdapter adapter = DapRegistry.parse(REGISTRY).getAdapters().get(0);

		assertThat(adapter.commandLine("/opt/adapters", "/tmp/program.js", 8123)).containsExactly("node",
				"/opt/adapters/adapter.js", "8123");
	}

	@Test
	void rejectsMalformedRegistries() {
		assertThatThrownBy(() -> DapRegistry.parse("{\"adapters\": 12}")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DapRegistry.parse("{}")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DapRegistry.parse(
				"{\"adapters\":[{\"id\":\"x\",\"name\":\"x\",\"command\":\"node\",\"downloads\":[{\"os\":\"linux\",\"arch\":\"x64\"}]}]}"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void bundledDownloadsUseHttpsAndArchiveTypes() {
		DapRegistry registry = DapRegistry.loadDefault();

		for (DapAdapter adapter : registry.getAdapters()) {
			for (DapDownload download : adapter.getDownloads()) {
				assertThat(download.url()).as("%s download", adapter.getId()).startsWith("https://");
				assertThat(download.archiveType()).as("%s archive type", adapter.getId()).isNotBlank();
			}
		}
	}

	@Test
	void bundlesADownloadForTheCurrentPlatform() {
		DapRegistry registry = DapRegistry.loadDefault();

		assertThat(registry.getAdapters()).isNotEmpty();
		assertThat(registry.supports(LangPlatform.LINUXX64)).isTrue();
		assertThat(registry.supports(LangPlatform.LINUXARM64)).isTrue();
		assertThat(registry.supports(LangPlatform.WINDOWSX64UNICODE)).isTrue();
		assertThat(registry.supports(LangPlatform.MACOSINTEL)).isTrue();
		assertThat(registry.supports(LangPlatform.MACOSAARCH64)).isTrue();
	}

}
