package io.simpleide.lsp.core;

import java.nio.file.Path;

/**
 * Text document identity and synchronization state for an LSP session.
 *
 * <p>
 * A document stores the normalized absolute path, the file URI sent to the language
 * server, the language id and the version that is incremented for every change. The open
 * and modified flags let the editor track the document lifecycle.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class Document {

	private final Path path;

	private final String uri;

	private final String languageId;

	private int version;

	private boolean opened;

	private boolean modified;

	/**
	 * Creates an opened, unmodified document with version {@code 1}.
	 * @param path document path
	 * @param languageId LSP language identifier
	 */
	public Document(Path path, String languageId) {
		this.path = path.toAbsolutePath().normalize();
		this.uri = this.path.toUri().toString();
		this.languageId = languageId;

		this.version = 1;
		this.opened = true;
		this.modified = false;
	}

	/**
	 * Returns the normalized absolute document path.
	 * @return the document path
	 */
	public Path getPath() {
		return path;
	}

	/**
	 * Returns the file URI sent to the language server.
	 * @return the document URI
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * Returns the LSP language identifier.
	 * @return the language id
	 */
	public String getLanguageId() {
		return languageId;
	}

	/**
	 * Returns the current document version.
	 * @return the version number
	 */
	public int getVersion() {
		return version;
	}

	/**
	 * Increments the document version after a change.
	 */
	public void incrementVersion() {
		version++;
	}

	/**
	 * Tells whether the document is currently open.
	 * @return {@code true} when the document is open
	 */
	public boolean isOpened() {
		return opened;
	}

	/**
	 * Updates the open state.
	 * @param opened new open state
	 */
	public void setOpened(boolean opened) {
		this.opened = opened;
	}

	/**
	 * Tells whether the document has unsaved changes.
	 * @return {@code true} when the document is modified
	 */
	public boolean isModified() {
		return modified;
	}

	/**
	 * Updates the modified state.
	 * @param modified new modified state
	 */
	public void setModified(boolean modified) {
		this.modified = modified;
	}

}
