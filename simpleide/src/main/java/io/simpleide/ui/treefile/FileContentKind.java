package io.simpleide.ui.treefile;

/**
 * Resolved presentation kind for a file opened in the editor area. Audio and video are
 * routed to dedicated read-only playback components when the platform supports JavaFX
 * media; otherwise they fall back to {@link #BINARY} and open in the hex editor.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public enum FileContentKind {

	/** Editable UTF-8 text, or the paged read-only view for large text. */
	TEXT,

	/** Image decoded through ImageIO or the bundled readers. */
	IMAGE,

	/** Audio played by the read-only media viewer. */
	AUDIO,

	/** Video played by the read-only media viewer. */
	VIDEO,

	/** Any other content, opened in the hex editor. */
	BINARY

}
