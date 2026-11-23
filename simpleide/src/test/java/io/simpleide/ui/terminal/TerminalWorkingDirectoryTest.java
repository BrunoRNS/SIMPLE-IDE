package io.simpleide.ui.terminal;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalWorkingDirectoryTest {

	@TempDir
	private Path tempDirectory;

	@Test
	void prefersTheOpenWorkspace() throws Exception {
		Path workspace = Files.createDirectory(tempDirectory.resolve("workspace")).toRealPath();
		Path home = Files.createDirectory(tempDirectory.resolve("home")).toRealPath();
		Path startup = Files.createDirectory(tempDirectory.resolve("startup")).toRealPath();

		assertEquals(workspace, TerminalWorkingDirectory.resolve(workspace, home.toString(), startup.toString()));
	}

	@Test
	void usesAccessibleHomeWhenNoWorkspaceIsOpen() throws Exception {
		Path home = Files.createDirectory(tempDirectory.resolve("home")).toRealPath();
		Path startup = Files.createDirectory(tempDirectory.resolve("startup")).toRealPath();

		assertEquals(home, TerminalWorkingDirectory.resolve(null, home.toString(), startup.toString()));
	}

	@Test
	void fallsBackToStartupDirectoryWhenHomeIsUnavailable() throws Exception {
		Path startup = Files.createDirectory(tempDirectory.resolve("startup")).toRealPath();

		assertEquals(startup, TerminalWorkingDirectory.resolve(null, tempDirectory.resolve("missing-home").toString(),
				startup.toString()));
	}

	@Test
	void failsExplicitlyWhenNeitherFallbackDirectoryIsAccessible() {
		String missingHome = tempDirectory.resolve("missing-home").toString();
		String missingStartup = tempDirectory.resolve("missing-startup").toString();

		assertThatThrownBy(() -> TerminalWorkingDirectory.resolve(null, missingHome, missingStartup))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
					"Neither the user home directory nor the application startup directory is accessible");
	}

}
