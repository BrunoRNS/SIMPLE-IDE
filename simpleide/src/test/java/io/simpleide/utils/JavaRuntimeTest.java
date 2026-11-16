package io.simpleide.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link io.simpleide.utils.JavaRuntime}.
 */
class JavaRuntimeTest {

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	@Test
	void acceptsJdkWithCompilerLauncher(@TempDir Path root) throws IOException {
		Path home = runtime(root, "jdk", true, true, null);
		assertTrue(JavaRuntime.isJdkHome(home));
	}

	@Test
	void acceptsJdkModuleWithoutCompilerLauncher(@TempDir Path root) throws IOException {
		Path home = runtime(root, "jpackage", true, false,
				"java.base java.compiler java.desktop jdk.compiler jdk.jartool");
		assertTrue(JavaRuntime.isJdkHome(home));
	}

	@Test
	void rejectsJreWithoutCompilerModule(@TempDir Path root) throws IOException {
		Path home = runtime(root, "jre", true, false, "java.base java.compiler java.desktop");
		assertFalse(JavaRuntime.isJdkHome(home));
	}

	@Test
	void rejectsRuntimeWithoutJavaLauncher(@TempDir Path root) throws IOException {
		Path home = runtime(root, "stripped", false, false, "java.base jdk.compiler");
		assertFalse(JavaRuntime.isJdkHome(home));
	}

	@Test
	void findsTheFirstUsableJdk(@TempDir Path root) throws IOException {
		Path jre = runtime(root, "jre", true, false, "java.base");
		Path jdk = runtime(root, "jdk", true, true, null);
		assertEquals(jdk, JavaRuntime.firstJdk(List.of(jre, jdk)).orElseThrow());
	}

	@Test
	void recognizesBareJavaCommands() {
		assertTrue(JavaRuntime.isJavaCommand("java"));
		assertTrue(JavaRuntime.isJavaCommand("java.exe"));
		assertTrue(JavaRuntime.isJavaCommand(" JAVA "));
		assertFalse(JavaRuntime.isJavaCommand("/opt/jdk/bin/java"));
		assertFalse(JavaRuntime.isJavaCommand("javac"));
		assertFalse(JavaRuntime.isJavaCommand(null));
	}

	@Test
	void usesConfiguredHomeBeforeAutomaticCandidates(@TempDir Path root) throws IOException {
		Path home = runtime(root, "configured", true, true, null);
		String previous = System.getProperty(JavaRuntime.JAVA_HOME_PROPERTY);
		try {
			System.setProperty(JavaRuntime.JAVA_HOME_PROPERTY, home.toString());
			List<Path> candidates = JavaRuntime.candidateHomes();
			assertEquals(home.toAbsolutePath().normalize(), candidates.get(0));
		}
		finally {
			if (previous == null) {
				System.clearProperty(JavaRuntime.JAVA_HOME_PROPERTY);
			}
			else {
				System.setProperty(JavaRuntime.JAVA_HOME_PROPERTY, previous);
			}
		}
	}

	private static Path runtime(Path root, String name, boolean java, boolean javac, String modules)
			throws IOException {
		Path home = Files.createDirectories(root.resolve(name));
		Path bin = Files.createDirectories(home.resolve("bin"));
		if (java) {
			executable(bin.resolve(WINDOWS ? "java.exe" : "java"));
		}
		if (javac) {
			executable(bin.resolve(WINDOWS ? "javac.exe" : "javac"));
		}
		if (modules != null) {
			Files.writeString(home.resolve("release"), "JAVA_VERSION=\"17\"\nMODULES=\"" + modules + "\"\n");
		}
		return home;
	}

	private static void executable(Path path) throws IOException {
		Files.createFile(path);
		if (!path.toFile().setExecutable(true, true) && !Files.isExecutable(path)) {
			throw new IOException("Could not make test file executable: " + path);
		}
	}

}
