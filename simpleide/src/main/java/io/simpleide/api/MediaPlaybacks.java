package io.simpleide.api;

import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates {@link MediaPlayback} components when the current platform provides JavaFX
 * media support.
 *
 * <p>
 * JavaFX publishes native artifacts only for Linux x64/arm64, macOS x64/arm64 and Windows
 * x64. The Maven build compiles the JavaFX-backed viewers only on those platforms, so a
 * call from any other platform finds no implementation class and returns {@code null}.
 * Callers should then present the file with the hex editor instead of a player.
 *
 * <p>
 * The lookup is reflective on purpose: the base application must keep building and
 * running where the JavaFX modules are absent, including 32-bit x86 builds.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class MediaPlaybacks {

	private static final Logger LOGGER = LoggerFactory.getLogger(MediaPlaybacks.class);

	private static final String AUDIO_VIEWER = "io.simpleide.api.AudioViewer";

	private static final String VIDEO_VIEWER = "io.simpleide.api.VideoViewer";

	private static final boolean AVAILABLE = probe();

	private MediaPlaybacks() {
	}

	/**
	 * Returns whether this build and platform can play audio and video.
	 * @return {@code true} when the JavaFX-backed viewer classes are present
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static boolean isAvailable() {
		return AVAILABLE;
	}

	/**
	 * Creates an audio player for a file, or returns {@code null} when playback is not
	 * available on this platform.
	 * @param path audio file to play
	 * @return the player, or {@code null} when no media implementation is available
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static MediaPlayback audio(Path path) {
		return create(AUDIO_VIEWER, path);
	}

	/**
	 * Creates a video player for a file, or returns {@code null} when playback is not
	 * available on this platform.
	 * @param path video file to play
	 * @return the player, or {@code null} when no media implementation is available
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static MediaPlayback video(Path path) {
		return create(VIDEO_VIEWER, path);
	}

	private static MediaPlayback create(String className, Path path) {
		Objects.requireNonNull(path, "path");
		if (!AVAILABLE) {
			return null;
		}
		try {
			Class<?> type = Class.forName(className, true, MediaPlaybacks.class.getClassLoader());
			return (MediaPlayback) type.getConstructor(Path.class).newInstance(path);
		}
		catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
			LOGGER.warn("Unable to create the media viewer {} for {}.", className, path, error);
			return null;
		}
	}

	private static boolean probe() {
		try {
			Class.forName(AUDIO_VIEWER, false, MediaPlaybacks.class.getClassLoader());
			return true;
		}
		catch (ClassNotFoundException | LinkageError error) {
			LOGGER.debug("JavaFX media viewers are not part of this build; media files use the hex editor.");
			return false;
		}
	}

}
