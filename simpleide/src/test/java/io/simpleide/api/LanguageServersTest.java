package io.simpleide.api;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.simpleide.lsp.dowload.LspManager;

/**
 * Tests {@link io.simpleide.api.LanguageServers}.
 */
class LanguageServersTest {

	@AfterEach
	void stopServers() {
		LanguageServers.shutdown();
	}

	@Test
	void mapsFilesToLanguageServerIdentifiers() {
		assertThat(LanguageServers.languageIdOf(Path.of("Main.java"))).isEqualTo("java");
		assertThat(LanguageServers.languageIdOf(Path.of("app.py"))).isEqualTo("python");
		assertThat(LanguageServers.languageIdOf(Path.of("index.js"))).isEqualTo("javascript");
		assertThat(LanguageServers.languageIdOf(Path.of("main.go"))).isEqualTo("go");
		assertThat(LanguageServers.languageIdOf(Path.of("Main.kt"))).isEqualTo("kotlin");
		assertThat(LanguageServers.languageIdOf(Path.of("script.groovy"))).isEqualTo("groovy");
		assertThat(LanguageServers.languageIdOf(Path.of("Program.cs"))).isEqualTo("csharp");
		assertThat(LanguageServers.languageIdOf(Path.of("main.rs"))).isEqualTo("rust");
		assertThat(LanguageServers.languageIdOf(Path.of("main.c"))).isEqualTo("c");
		assertThat(LanguageServers.languageIdOf(Path.of("README.md"))).isEqualTo("plaintext");
	}

	@Test
	void reportsRegistrySupportForALanguage() {
		boolean pythonRegistered = LspManager.getRegistry()
			.getServers()
			.stream()
			.anyMatch(server -> server.getLanguageId().equals("python") && server.isSupportedOnCurrentPlatform());

		assertThat(LanguageServers.isSupported("python")).isEqualTo(pythonRegistered);
		assertThat(LanguageServers.isSupported("plaintext")).isFalse();
		assertThat(LanguageServers.isSupported("")).isFalse();
		assertThat(LanguageServers.isSupported((String) null)).isFalse();
	}

	@Test
	void failsFastForFilesWithoutALanguageServer() {
		assertThatThrownBy(() -> LanguageServers.connect(Path.of("README.md"), null).join())
			.isInstanceOf(CompletionException.class)
			.hasCauseInstanceOf(IllegalStateException.class);
	}

	@Test
	void shutdownIsIdempotent() {
		LanguageServers.shutdown();
		LanguageServers.shutdown();
	}

}
