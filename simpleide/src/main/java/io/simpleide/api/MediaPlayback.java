package io.simpleide.api;

import javax.swing.JComponent;

/**
 * Read-only audio or video playback component embedded in Swing.
 *
 * <p>
 * The implementation is provided by {@link MediaPlaybacks}. It is generic because the
 * JavaFX-backed viewers are compiled only on platforms that publish JavaFX native
 * artifacts; on every other platform no playback component exists and media files open in
 * the hex editor.
 *
 * <p>
 * Implementations load media asynchronously. They never throw from {@link #play()},
 * {@link #pause()}, {@link #stop()} or {@link #setVolume(float)} before the media is
 * ready; loading errors are reported by the component itself.
 *
 * @author luisotavio13
 * @since v1.1.0
 * @see MediaPlaybacks
 */
public interface MediaPlayback extends AutoCloseable {

	/**
	 * Returns the Swing component that renders the player.
	 * @return the embeddable player component
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	JComponent component();

	/**
	 * Returns whether the media backend created a player.
	 * @return {@code true} after media initialization succeeds
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	boolean isMediaLoaded();

	/**
	 * Starts or resumes playback.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	void play();

	/**
	 * Pauses playback while preserving the current position.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	void pause();

	/**
	 * Stops playback and returns to the beginning.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	void stop();

	/**
	 * Sets the playback volume.
	 * @param value volume from {@code 0} (muted) through {@code 1} (full volume)
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	void setVolume(float value);

	/**
	 * Releases playback resources. Calling the method more than once is a no-op.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	@Override
	void close();

}
