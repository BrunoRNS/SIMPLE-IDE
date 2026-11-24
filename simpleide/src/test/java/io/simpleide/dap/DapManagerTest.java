package io.simpleide.dap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.simpleide.language.config.LangPlatform;

/**
 * Tests {@link io.simpleide.dap.DapManager}.
 */
class DapManagerTest {

	private static final String REGISTRY = """
			{
			  "adapters": [
			    {
			      "id": "test-adapter",
			      "name": "Test Adapter",
			      "version": "1.0.0",
			      "command": "node",
			      "downloads": [
			        {"os": "linux", "arch": "x64", "url": "https://example.invalid/adapter.tar.gz",
			         "archiveType": "tar.gz"}
			      ]
			    }
			  ]
			}
			""";

	@Test
	void reportsUnsupportedPlatformsWithoutDownloading() {
		DapManager manager = new DapManager(DapRegistry.parse(REGISTRY), LangPlatform.WINDOWSARM64);

		assertThat(manager.isSupported()).isFalse();
		assertThatThrownBy(manager::prepare).isInstanceOf(DapManager.DapUnavailableException.class)
			.hasMessageContaining("not supported");
	}

	@Test
	void recognisesSupportedPlatforms() {
		DapManager manager = new DapManager(DapRegistry.parse(REGISTRY), LangPlatform.LINUXX64);

		assertThat(manager.isSupported()).isTrue();
	}

}
