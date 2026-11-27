package io.simpleide.ui.sidebar;

import java.util.List;
import java.util.Objects;

import io.simpleide.git.GitCommit;

/**
 * Swimlane state of a single commit graph row.
 *
 * @param commit commit rendered in this row
 * @param input lanes reaching this row from the previous one
 * @param output lanes leaving this row towards the next one
 * @author LuisOtavio13
 * @since v1.1.0
 */
record GitGraphRow(GitCommit commit, List<GitGraphLane> input, List<GitGraphLane> output) {

	GitGraphRow {
		Objects.requireNonNull(commit, "commit");
		input = List.copyOf(Objects.requireNonNull(input, "input"));
		output = List.copyOf(Objects.requireNonNull(output, "output"));
	}

	/**
	 * Returns the number of lanes needed to render this row.
	 * @return lane count
	 */
	int laneCount() {
		return Math.max(1, Math.max(input.size(), output.size()));
	}

	/**
	 * Finds the input lane carrying this row's commit.
	 * @return lane index, or {@code -1} when the commit starts a new lane
	 */
	int inputIndex() {
		for (int index = 0; index < input.size(); index++) {
			if (input.get(index).id().equals(commit.hash())) {
				return index;
			}
		}

		return -1;
	}

	/**
	 * Returns the index of the lane where this row's commit node is drawn. When the
	 * commit is not present in the input lanes, the node is appended after them.
	 * @return node lane index
	 */
	int circleIndex() {
		int index = inputIndex();

		return index >= 0 ? index : input.size();
	}

	/**
	 * Finds the last output lane heading to a parent commit.
	 * @param id parent commit hash
	 * @return lane index, or {@code -1} when the parent is not present
	 */
	int lastOutputIndex(String id) {
		for (int index = output.size() - 1; index >= 0; index--) {
			if (output.get(index).id().equals(id)) {
				return index;
			}
		}

		return -1;
	}

}
