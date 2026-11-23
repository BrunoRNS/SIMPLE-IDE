package io.simpleide.git;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Commit information displayed in the Source Control panel.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public record GitCommit(String hash, String abbreviatedHash, String author, String message, Instant date,
		List<String> parents, int lane, List<String> refs) {

	/**
	 * Creates immutable commit information.
	 * @param hash full commit hash
	 * @param abbreviatedHash short commit hash
	 * @param author commit author
	 * @param message commit message
	 * @param date commit timestamp
	 * @param parents parent commit hashes
	 * @param lane graph lane assigned by JGit
	 * @param refs branches and tags pointing to this commit
	 */
	public GitCommit {
		Objects.requireNonNull(hash, "hash");
		Objects.requireNonNull(date, "date");
		parents = List.copyOf(Objects.requireNonNull(parents, "parents"));
		refs = List.copyOf(Objects.requireNonNull(refs, "refs"));
	}
}
