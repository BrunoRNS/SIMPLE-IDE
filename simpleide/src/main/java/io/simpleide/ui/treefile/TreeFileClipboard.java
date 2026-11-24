package io.simpleide.ui.treefile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Clipboard for copy/cut/paste operations in the project tree.
 *
 * <p>
 * Holds a list of {@link TreeFile} entries along with the operation type (copy or cut) so
 * that paste can perform the correct action.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 */
public final class TreeFileClipboard {

	private static final TreeFileClipboard INSTANCE = new TreeFileClipboard();

	private List<TreeFile> entries = Collections.emptyList();

	private boolean cut = false;

	private TreeFileClipboard() {
	}

	/**
	 * Returns the singleton clipboard instance.
	 * @return the clipboard
	 */
	public static TreeFileClipboard getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the current clipboard entries.
	 * @return unmodifiable list of entries, or empty list if clipboard is empty
	 */
	public List<TreeFile> getEntries() {
		return entries;
	}

	/**
	 * Returns whether the current operation is a cut (move) rather than a copy.
	 * @return {@code true} if the entries were cut, {@code false} if copied
	 */
	public boolean isCut() {
		return cut;
	}

	/**
	 * Returns whether the clipboard is empty.
	 * @return {@code true} if there are no entries in the clipboard
	 */
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * Sets the clipboard contents to the given entries with a copy operation.
	 * @param entries the entries to copy
	 * @throws NullPointerException if entries is null or contains null
	 */
	public void setCopied(List<TreeFile> entries) {
		Objects.requireNonNull(entries, "entries");
		if (entries.stream().anyMatch(Objects::isNull)) {
			throw new NullPointerException("entries must not contain null");
		}
		this.entries = List.copyOf(entries);
		this.cut = false;
	}

	/**
	 * Sets the clipboard contents to the given entries with a cut operation.
	 * @param entries the entries to cut
	 * @throws NullPointerException if entries is null or contains null
	 */
	public void setCut(List<TreeFile> entries) {
		Objects.requireNonNull(entries, "entries");
		if (entries.stream().anyMatch(Objects::isNull)) {
			throw new NullPointerException("entries must not contain null");
		}
		this.entries = List.copyOf(entries);
		this.cut = true;
	}

	/**
	 * Clears the clipboard.
	 */
	public void clear() {
		this.entries = Collections.emptyList();
		this.cut = false;
	}

	/**
	 * Returns the source paths of the clipboard entries.
	 * @return list of source paths
	 */
	public List<Path> getSourcePaths() {
		return entries.stream().map(TreeFile::getPath).toList();
	}

}