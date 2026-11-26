package io.simpleide.dap;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link JavaDebugInstaller} without network access.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
class JavaDebugInstallerTest {

	@TempDir
	Path tempDirectory;

	@Test
	void readsTheOsgiExecutionEnvironment() throws Exception {
		Path jar = writeJar("osgi", "Require-Capability", "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=21))\"");

		assertThat(JavaDebugInstaller.requiredJavaFeature(jar)).isEqualTo(21);
	}

	@Test
	void readsTheLegacyExecutionEnvironment() throws Exception {
		Path jar = writeJar("legacy", "Bundle-RequiredExecutionEnvironment", "JavaSE-11");

		assertThat(JavaDebugInstaller.requiredJavaFeature(jar)).isEqualTo(11);
	}

	@Test
	void cachesInstallationsUnderTheDapDirectory() {
		assertThat(JavaDebugInstaller.getInstallDirectory("0.60.0"))
			.isEqualTo(Path.of(System.getProperty("user.home"), ".simpleide", "dap", "java-debug-0.60.0"));
	}

	private Path writeJar(String name, String attribute, String value) throws Exception {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(new Attributes.Name(attribute), value);
		Path jar = tempDirectory.resolve("com.microsoft.java.debug.plugin-" + name + ".jar");
		try (OutputStream output = Files.newOutputStream(jar);
				JarOutputStream archive = new JarOutputStream(output, manifest)) {
			archive.flush();
		}
		return jar;
	}

}
