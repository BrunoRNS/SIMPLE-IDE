package io.simpleide.git;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One file entry reported by Git.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public record GitStatusEntry(Path path, GitStatus status) {

	/**
	 * Creates a Git status entry.
	 * @param path repository-relative file path
	 * @param status file status
	 */
	public GitStatusEntry {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(status, "status");
	}

	/**
	 * Status categories shown in the Source Control panel.
	 *
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public enum GitStatus {

		/** Added or untracked file. */
		ADDED,

		/** Modified or renamed file. */
		MODIFIED,

		/** Deleted file. */
		DELETED;

	}
}
