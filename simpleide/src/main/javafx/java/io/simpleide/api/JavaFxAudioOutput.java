package io.simpleide.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures the JavaFX audio output used by the media viewers.
 *
 * <p>
 * JavaFX media on Linux writes through ALSA. When PulseAudio or PipeWire is available but
 * no user or system ALSA configuration exists, this class points {@code ALSA_CONFIG_PATH}
 * at a temporary configuration that routes the default PCM through the PulseAudio ALSA
 * plugin. The configuration is a no-op on other platforms, when an ALSA configuration is
 * already present, or when no PulseAudio socket exists.
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
	 * Installs the PulseAudio ALSA fallback once for the current process.
	 * @throws IOException when the temporary ALSA configuration cannot be created or
	 * applied
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	static synchronized void configurePulseFallback() throws IOException {
		if (configured) {
			return;
		}
		if (!System.getProperty("os.name", "").toLowerCase().contains("linux")
				|| System.getenv("ALSA_CONFIG_PATH") != null || hasAlsaOverride() || !hasPulseSocket()) {
			configured = true;
			return;
		}

		Path config = Files.createTempFile("simpleide-alsa-", ".conf");
		try {
			Files.writeString(config, "pcm.!default { type pulse }\nctl.!default { type pulse }\n");
			config.toFile().deleteOnExit();
			if (Native.load("c", LibC.class).setenv("ALSA_CONFIG_PATH", config.toString(), 0) != 0) {
				throw new IOException("Unable to configure the ALSA PulseAudio output.");
			}
			configured = true;
			LOGGER.info("Routing JavaFX media audio through the available PulseAudio-compatible ALSA output.");
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
			throw new IOException("Unable to configure the ALSA PulseAudio output.", error);
		}
	}

	private static boolean hasAlsaOverride() {

		return Files.exists(Path.of(System.getProperty("user.home", "."), ".asoundrc"))
				|| Files.exists(Path.of("/etc/asound.conf"));
	}

	private static boolean hasPulseSocket() {
		String runtimeDirectory = System.getenv("XDG_RUNTIME_DIR");
		return runtimeDirectory != null && Files.exists(Path.of(runtimeDirectory, "pulse", "native"));
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
