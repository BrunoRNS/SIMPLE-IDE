package io.simpleide.dap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests adapter installation markers and failure cleanup.
 */
class DapInstallerTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void checksTheInstalledVersionMarker() throws IOException {
		withHome(() -> {
			DapAdapter adapter = adapter("1.2.3", "https://example.com/adapter.zip");
			Path directory = DapInstaller.getInstallDirectory(adapter);

			assertThat(DapInstaller.isInstalled(adapter)).isFalse();
			Files.createDirectories(directory);
			Files.writeString(directory.resolve(".installed"), "1.2.3");

			assertThat(DapInstaller.isInstalled(adapter)).isTrue();
			Files.writeString(directory.resolve(".installed"), "older");
			assertThat(DapInstaller.isInstalled(adapter)).isFalse();
		});
	}

	@Test
	void removesPartialInstallationAfterRejectingInsecureDownload() {
		withHome(() -> {
			DapAdapter adapter = adapter("1.0.0", "http://example.com/adapter.zip");
			Path directory = DapInstaller.getInstallDirectory(adapter);

			assertThatThrownBy(() -> DapInstaller.install(adapter, adapter.getDownloads().get(0)))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("HTTPS");
			assertThat(directory).doesNotExist();
		});
	}

	private void withHome(ThrowingRunnable action) {
		String originalHome = System.getProperty("user.home");
		System.setProperty("user.home", temporaryDirectory.toString());
		try {
			action.run();
		}
		catch (Exception error) {
			throw new AssertionError(error);
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

	private static DapAdapter adapter(String version, String url) {
		return new DapAdapter("test-adapter", "Test Adapter", version, "node", List.of(),
				List.of(new DapDownload("linux", "x64", url, "zip")));
	}

	@FunctionalInterface
	private interface ThrowingRunnable {

		void run() throws Exception;

	}

}
