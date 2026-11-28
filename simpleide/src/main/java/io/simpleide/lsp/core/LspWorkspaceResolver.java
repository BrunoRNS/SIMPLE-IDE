package io.simpleide.lsp.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import io.simpleide.utils.JavaRuntime;

/**
 * Resolves workspace roots and the environment used to start language servers.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LspWorkspaceResolver {

	private static final Set<String> WORKSPACE_MARKERS = Set.of("pom.xml", "build.gradle", "build.gradle.kts",
			"settings.gradle", "settings.gradle.kts", "Cargo.toml", "package.json", "pyproject.toml", "setup.py",
			"go.mod", "compile_commands.json");

	private LspWorkspaceResolver() {
	}

	static Map<String, String> getEnvironment(String languageId) {
		if ("java".equals(languageId)) {
			String configuredHome = System.getProperty(JavaRuntime.LSP_JAVA_HOME_PROPERTY);
			if (configuredHome != null && !configuredHome.isBlank()) {
				Path path = Path.of(configuredHome).toAbsolutePath().normalize();
				if (!Files.isDirectory(path)) {
					throw new IllegalArgumentException("Configured LSP Java home is not a directory: " + path);
				}
			}
		}
		return JavaRuntime.environment();
	}

	static Path findWorkspaceRoot(Path documentPath) {
		Path directory = documentPath.toAbsolutePath().normalize().getParent();
		if (directory == null) {
			return null;
		}
		for (Path current = directory; current != null; current = current.getParent()) {
			for (String marker : WORKSPACE_MARKERS) {
				if (Files.exists(current.resolve(marker))) {
					return current;
				}
			}
		}
		return directory;
	}

}
