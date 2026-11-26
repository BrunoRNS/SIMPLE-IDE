package io.simpleide.dap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.simpleide.language.config.LangPlatform;

/**
 * Integration tests covering the bundled {@code registry.json}: every supported operating
 * system and architecture must resolve to a debug adapter, and each adapter must ship
 * downloads for every platform it declares support for.
 */
@Tag("debug")
class DapRegistryIntegrationTest {

	@Test
	void bundledRegistrySupportsEveryLanguagePlatform() {
		DapRegistry registry = DapRegistry.loadDefault();

		assertThat(registry.getAdapters()).isNotEmpty();
		for (LangPlatform platform : LangPlatform.values()) {
			if (platform == LangPlatform.UNKNOWN) {
				continue;
			}
			assertThat(registry.supports(platform)).as("registry has a download for %s", platform.getDisplayName())
				.isTrue();
			DapAdapter adapter = registry.findForPlatform(platform);
			assertThat(adapter).isNotNull();
			assertThat(adapter.downloadFor(platform)).isNotNull();
		}
	}

	@Test
	void eachAdapterCoversAllOperatingSystemsAndArchitectures() {
		DapRegistry registry = DapRegistry.loadDefault();

		for (DapAdapter adapter : registry.getAdapters()) {
			List<LangPlatform> platforms = List.of(LangPlatform.WINDOWSX86, LangPlatform.WINDOWSX64UNICODE,
					LangPlatform.WINDOWSARM64, LangPlatform.LINUXX86, LangPlatform.LINUXX64, LangPlatform.LINUXARM64,
					LangPlatform.MACOSINTEL, LangPlatform.MACOSAARCH64);
			for (LangPlatform platform : platforms) {
				assertThat(adapter.downloadFor(platform))
					.as("%s download for %s", adapter.getId(), platform.getDisplayName())
					.isNotNull();
			}
		}
	}

}
