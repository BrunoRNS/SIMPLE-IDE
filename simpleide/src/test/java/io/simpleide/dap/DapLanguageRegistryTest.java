package io.simpleide.dap;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.simpleide.language.config.LangPlatform;

/**
 * Verifies that the bundled registry resolves a debug adapter for every language the IDE
 * can debug and that each language uses the expected transport.
 *
 * <p>
 * Go, Python and JavaScript use standalone adapters reached over TCP; Java is hosted by
 * the JDT language server and uses the {@code jdtls} transport.
 */
class DapLanguageRegistryTest {

	@Test
	void resolvesAdaptersForEverySupportedLanguage() {
		DapRegistry registry = DapRegistry.loadDefault();

		assertThat(registry.supports(LangPlatform.LINUXX64, "go")).isTrue();
		assertThat(registry.supports(LangPlatform.LINUXX64, "python")).isTrue();
		assertThat(registry.supports(LangPlatform.LINUXX64, "javascript")).isTrue();
		assertThat(registry.supports(LangPlatform.LINUXX64, "java")).isTrue();

		assertThat(registry.findForPlatform(LangPlatform.LINUXX64, "go").getTransport()).isEqualTo("tcp");
		assertThat(registry.findForPlatform(LangPlatform.LINUXX64, "python").getTransport()).isEqualTo("tcp");
		assertThat(registry.findForPlatform(LangPlatform.LINUXX64, "javascript").getTransport()).isEqualTo("tcp");
		assertThat(registry.findForPlatform(LangPlatform.LINUXX64, "java").getTransport()).isEqualTo("jdtls");
	}

	@Test
	void mapsSourceFilesToLanguages() {
		assertThat(DapManager.languageOf(Path.of("src/Main.java"))).isEqualTo("java");
		assertThat(DapManager.languageOf(Path.of("src/app.py"))).isEqualTo("python");
		assertThat(DapManager.languageOf(Path.of("src/index.js"))).isEqualTo("javascript");
		assertThat(DapManager.languageOf(Path.of("src/main.go"))).isEqualTo("go");
		assertThat(DapManager.languageOf(Path.of("README.md"))).isEmpty();
	}

	@Test
	void flagsJavaAsLanguageServerDebug() {
		DapManager manager = new DapManager(DapRegistry.loadDefault(), LangPlatform.LINUXX64);

		assertThat(manager.isLanguageServerDebug(Path.of("Main.java"))).isTrue();
		assertThat(manager.isLanguageServerDebug(Path.of("app.py"))).isFalse();
		assertThat(manager.isLanguageServerDebug(Path.of("index.js"))).isFalse();
		assertThat(manager.isLanguageServerDebug(Path.of("main.go"))).isFalse();
	}

	@Test
	void everyLanguageAdapterShipsDownloadsForEveryPlatform() {
		DapRegistry registry = DapRegistry.loadDefault();
		List<LangPlatform> platforms = List.of(LangPlatform.WINDOWSX86, LangPlatform.WINDOWSX64UNICODE,
				LangPlatform.WINDOWSARM64, LangPlatform.LINUXX86, LangPlatform.LINUXX64, LangPlatform.LINUXARM64,
				LangPlatform.MACOSINTEL, LangPlatform.MACOSAARCH64);

		for (String language : List.of("go", "python", "javascript", "java")) {
			DapAdapter adapter = registry.findForPlatform(LangPlatform.LINUXX64, language);
			assertThat(adapter).as("adapter for %s", language).isNotNull();
			for (LangPlatform platform : platforms) {
				assertThat(adapter.downloadFor(platform)).as("%s download for %s", language, platform.getDisplayName())
					.isNotNull();
			}
		}
	}

}