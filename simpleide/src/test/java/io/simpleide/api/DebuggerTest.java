package io.simpleide.api;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.simpleide.dap.DapManager;
import io.simpleide.dap.DapSession;

/**
 * Tests {@link io.simpleide.api.Debugger}.
 */
class DebuggerTest {

	@Test
	void mapsSourceFilesToDebugLanguages() {
		assertThat(Debugger.languageOf(Path.of("src/main.py"))).isEqualTo("python");
		assertThat(Debugger.languageOf(Path.of("src/main.go"))).isEqualTo("go");
		assertThat(Debugger.languageOf(Path.of("src/index.js"))).isEqualTo("javascript");
		assertThat(Debugger.languageOf(Path.of("src/Main.java"))).isEqualTo("java");
		assertThat(Debugger.languageOf(Path.of("notes.md"))).isEmpty();
	}

	@Test
	void reportsSupportForDebuggableFiles() {
		assertThat(Debugger.isSupported(Path.of("app.py"))).isTrue();
		assertThat(Debugger.isSupported(Path.of("main.go"))).isTrue();
		assertThat(Debugger.isSupported(Path.of("index.js"))).isTrue();
		assertThat(Debugger.isSupported(Path.of("Main.java"))).isTrue();
		assertThat(Debugger.isSupported(Path.of("main.rs"))).isFalse();
		assertThat(Debugger.isSupported(null)).isFalse();
	}

	@Test
	void flagsJavaAsALanguageServerDebugLanguage() {
		assertThat(Debugger.isLanguageServerDebug(Path.of("Main.java"))).isTrue();
		assertThat(Debugger.isLanguageServerDebug(Path.of("app.py"))).isFalse();
		assertThat(Debugger.isLanguageServerDebug(Path.of("main.rs"))).isFalse();
	}

	@Test
	void rejectsJavaInTheStandaloneAdapterEntryPoint() {
		assertThatThrownBy(() -> Debugger.start(Path.of("Main.java"), List.of(4), new DapSession.Listener() {
		})).isInstanceOf(DapManager.DapUnavailableException.class).hasMessageContaining("use startJava");
	}

	@Test
	void reportsAMissingLanguageServerForJavaDebug() {
		assertThatThrownBy(() -> Debugger.startJava(Path.of("Main.java"), null, List.of(4), null))
			.isInstanceOf(DapManager.DapUnavailableException.class)
			.hasMessageContaining("language server");
	}

}
