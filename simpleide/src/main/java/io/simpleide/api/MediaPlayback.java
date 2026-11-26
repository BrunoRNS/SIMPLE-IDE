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
 * @see MediaPlaybacks
 * @author BrunoRNS
 * @since v1.1.0
 */
public interface MediaPlayback extends AutoCloseable {

	/**
	 * Returns the Swing component that renders the player.
	 * @return the embeddable player component
	 */
	JComponent component();

	/**
	 * Returns whether the media backend created a player.
	 * @return {@code true} after media initialization succeeds
	 */
	boolean isMediaLoaded();

	/**
	 * Starts or resumes playback.
	 */
	void play();

	/**
	 * Pauses playback while preserving the current position.
	 */
	void pause();

	/**
	 * Stops playback and returns to the beginning.
	 */
	void stop();

	/**
	 * Sets the playback volume.
	 * @param value volume from {@code 0} (muted) through {@code 1} (full volume)
	 */
	void setVolume(float value);

	/**
	 * Releases playback resources. Calling the method more than once is a no-op.
	 */
	@Override
	void close();

}
