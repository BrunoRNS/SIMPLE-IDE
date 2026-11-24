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
 * JavaFX publishes native artifacts only for Linux x64, macOS x64/arm64 and Windows x64.
 * Linux arm64 is deliberately built without JavaFX. The Maven build compiles the
 * JavaFX-backed viewers only when a compatible dependency variant is selected, so a call
 * from any other platform finds no usable implementation and returns {@code null}.
 * Callers should then present the file with the hex editor instead of a player.
 *
 * <p>
 * The lookup is reflective on purpose: the base application must keep building and
 * running where the JavaFX modules are absent, including 32-bit x86 builds and builds
 * that force the {@code nojavafx} consumer variant.
 *
 * @author luisotavio13
 * @since v1.1.0
 */
public final class MediaPlaybacks {

	private static final Logger LOGGER = LoggerFactory.getLogger(MediaPlaybacks.class);

	private static final String AUDIO_VIEWER = "io.simpleide.api.AudioViewer";

	private static final String VIDEO_VIEWER = "io.simpleide.api.VideoViewer";

	private static final String JAVAFX_MEDIA_PLAYER = "javafx.scene.media.MediaPlayer";

	private static final boolean AVAILABLE = probe();

	private MediaPlaybacks() {
	}

	/**
	 * Returns whether this build and platform can play audio and video.
	 * @return {@code true} when the JavaFX-backed viewer classes are present
	 * @author luisotavio13
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
	 * @author luisotavio13
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
	 * @author luisotavio13
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
			ClassLoader loader = MediaPlaybacks.class.getClassLoader();
			// A consumer can force the nojavafx variant for a jar that still contains the
			// viewer classes. Checking JavaFX itself keeps isAvailable() accurate in that
			// case, so files are classified as binary instead of failing at open time.
			Class.forName(AUDIO_VIEWER, false, loader);
			Class.forName(JAVAFX_MEDIA_PLAYER, false, loader);
			return true;
		}
		catch (ClassNotFoundException | LinkageError error) {
			LOGGER.debug("JavaFX media viewers are not available; media files use the hex editor.");
			return false;
		}
	}

}
