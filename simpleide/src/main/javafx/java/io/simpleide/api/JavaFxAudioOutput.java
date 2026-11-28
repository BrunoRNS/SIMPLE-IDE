package io.simpleide.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the JavaFX audio output used by the media viewers.
 *
 * <p>
 * JavaFX media on Linux writes through ALSA. When PulseAudio or PipeWire is available but
 * no user or system ALSA configuration defines a default device, this class points
 * {@code ALSA_CONFIG_PATH} at a temporary configuration that routes the default PCM
 * through the matching ALSA plugin (PipeWire when its module is installed, otherwise
 * PulseAudio). The configuration is a no-op on other platforms, when an ALSA default is
 * already configured, or when no compatible ALSA plugin module is installed.
 *
 * @author luisotavio13
 * @since v1.1.0
 */
final class JavaFxAudioOutput {

	private static final Logger LOGGER = LoggerFactory.getLogger(JavaFxAudioOutput.class);

	private static boolean configured;

	private JavaFxAudioOutput() {
	}

	/**
	 * Installs the PulseAudio or PipeWire ALSA fallback once for the current process.
	 * @throws IOException when the temporary ALSA configuration cannot be created or
	 * applied
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	static synchronized void configureAlsaFallback() throws IOException {
		if (configured) {
			return;
		}
		if (!System.getProperty("os.name", "").toLowerCase().contains("linux")
				|| System.getenv("ALSA_CONFIG_PATH") != null || hasAlsaDefault()) {
			configured = true;
			return;
		}
		String plugin = availablePlugin();
		if (plugin == null) {
			configured = true;
			LOGGER.debug("No PulseAudio or PipeWire ALSA plugin is installed; keeping the system ALSA configuration.");
			return;
		}

		Path config = Files.createTempFile("simpleide-alsa-", ".conf");
		try {
			Files.writeString(config,
					"pcm.!default { type " + plugin + " }\nctl.!default { type " + plugin + " }\n");
			config.toFile().deleteOnExit();
			if (Native.load("c", LibC.class).setenv("ALSA_CONFIG_PATH", config.toString(), 0) != 0) {
				throw new IOException("Unable to configure the " + plugin + " ALSA output.");
			}
			configured = true;
			LOGGER.info("Routing JavaFX media audio through the available {} ALSA output.", plugin);
		}
		catch (IOException | RuntimeException | LinkageError error) {
			try {
				Files.deleteIfExists(config);
			}
			catch (IOException cleanupError) {
				error.addSuppressed(cleanupError);
			}
			if (error instanceof IOException ioException) {
				throw ioException;
			}
			throw new IOException("Unable to configure the " + plugin + " ALSA output.", error);
		}
	}

	/**
	 * Returns whether the system already defines a default PCM. Distribution packages
	 * ship these definitions under {@code /etc/alsa/conf.d} (for example the PipeWire
	 * default on Ubuntu), so checking only the legacy asound files would override a
	 * working configuration.
	 */
	private static boolean hasAlsaDefault() {
		if (Files.exists(Path.of(System.getProperty("user.home", "."), ".asoundrc"))
				|| Files.exists(Path.of("/etc/asound.conf"))) {
			return true;
		}
		return definesDefault(Path.of("/etc/alsa/conf.d")) || definesDefault(Path.of("/usr/share/alsa/alsa.conf.d"));
	}

	private static boolean definesDefault(Path directory) {
		if (!Files.isDirectory(directory)) {
			return false;
		}
		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(file -> file.getFileName().toString().endsWith(".conf"))
				.anyMatch(file -> containsDefault(file));
		}
		catch (IOException | RuntimeException error) {
			LOGGER.debug("Unable to inspect the ALSA configuration in {}.", directory, error);
			return false;
		}
	}

	private static boolean containsDefault(Path file) {
		try {
			String content = Files.readString(file);
			return content.contains("pcm.!default") || content.contains("pcm.default");
		}
		catch (IOException | RuntimeException error) {
			LOGGER.debug("Unable to read the ALSA configuration {}.", file, error);
			return false;
		}
	}

	/**
	 * Returns the ALSA plugin that can talk to the running sound server, or {@code null}
	 * when neither plugin module is installed. Selecting the plugin by installed module
	 * avoids pointing ALSA at a plugin it cannot load, which makes JavaFX fail with
	 * {@code Could not create player!}.
	 */
	private static String availablePlugin() {
		for (String plugin : List.of("pipewire", "pulse")) {
			if (hasSocket(plugin) && hasPluginModule(plugin)) {
				return plugin;
			}
		}
		return null;
	}

	private static boolean hasSocket(String plugin) {
		String runtimeDirectory = System.getenv("XDG_RUNTIME_DIR");
		if (runtimeDirectory == null) {
			return false;
		}
		Path socket = "pipewire".equals(plugin) ? Path.of(runtimeDirectory, "pipewire-0")
				: Path.of(runtimeDirectory, "pulse", "native");
		return Files.exists(socket);
	}

	private static boolean hasPluginModule(String plugin) {
		String module = "libasound_module_pcm_" + plugin + ".so";
		for (Path directory : alsaModuleDirectories()) {
			if (Files.exists(directory.resolve(module))) {
				return true;
			}
		}
		return false;
	}

	private static List<Path> alsaModuleDirectories() {
		String architecture = System.getProperty("os.arch", "").toLowerCase();
		String multiarch = switch (architecture) {
			case "amd64", "x86_64" -> "x86_64-linux-gnu";
			case "aarch64", "arm64" -> "aarch64-linux-gnu";
			default -> architecture + "-linux-gnu";
		};
		return List.of(Path.of("/usr/lib", multiarch, "alsa-lib"), Path.of("/usr/lib/alsa-lib"),
				Path.of("/usr/lib64/alsa-lib"), Path.of("/usr/local/lib/alsa-lib"));
	}

	/**
	 * Minimal C library binding used to set an environment variable in the current
	 * process.
	 */
	private interface LibC extends Library {

		/**
		 * Sets an environment variable.
		 * @param name variable name
		 * @param value variable value
		 * @param overwrite non-zero to replace an existing value
		 * @return {@code 0} on success
		 */
		int setenv(String name, String value, int overwrite);

	}

}
