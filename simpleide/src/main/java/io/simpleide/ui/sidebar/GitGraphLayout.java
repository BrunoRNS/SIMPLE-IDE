package io.simpleide.ui.sidebar;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.simpleide.git.GitCommit;

/**
 * Assigns graph lanes to commits, using the same swimlane model as the VS Code Source
 * Control Graph.
 * <p>
 * Lanes flow from the previous row ({@code input}) into the next one ({@code output}):
 * the first parent inherits the lane of the commit, extra parents open new lanes, and
 * duplicate lanes heading to the same commit are collapsed. Every {@link GitGraphRow} can
 * therefore be drawn on its own, without looking at the other rows.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 */
final class GitGraphLayout {

	private static final Color[] LANE_COLORS = { new Color(86, 156, 214), new Color(198, 120, 221),
			new Color(152, 195, 121), new Color(229, 192, 123), new Color(224, 108, 117), new Color(86, 182, 194) };

	private final List<GitGraphRow> rows;

	private final int laneCount;

	private GitGraphLayout(List<GitGraphRow> rows) {
		this.rows = rows;
		this.laneCount = rows.stream().mapToInt(GitGraphRow::laneCount).max().orElse(1);
	}

	/**
	 * Builds the lane state for every commit.
	 * @param commits commits ordered from newest to oldest
	 * @return graph layout
	 */
	static GitGraphLayout build(List<GitCommit> commits) {
		Objects.requireNonNull(commits, "commits");

		List<GitGraphRow> rows = new ArrayList<>(commits.size());

		List<GitGraphLane> previous = List.of();

		int colorIndex = -1;

		for (GitCommit commit : commits) {
			List<GitGraphLane> output = new ArrayList<>();

			boolean firstParentAdded = false;

			for (GitGraphLane lane : previous) {
				if (lane.id().equals(commit.hash())) {
					if (!firstParentAdded && !commit.parents().isEmpty()) {
						output.add(new GitGraphLane(commit.parents().get(0), lane.color()));
						firstParentAdded = true;
					}

					continue;
				}

				output.add(lane);
			}

			for (int index = firstParentAdded ? 1 : 0; index < commit.parents().size(); index++) {
				colorIndex = Math.floorMod(colorIndex + 1, LANE_COLORS.length);
				output.add(new GitGraphLane(commit.parents().get(index), LANE_COLORS[colorIndex]));
			}

			rows.add(new GitGraphRow(commit, previous, output));

			previous = List.copyOf(output);
		}

		return new GitGraphLayout(List.copyOf(rows));
	}

	/**
	 * Creates a layout without commits.
	 * @return empty layout
	 */
	static GitGraphLayout empty() {
		return new GitGraphLayout(List.of());
	}

	/**
	 * Returns the fallback lane color.
	 * @return default color
	 */
	static Color defaultLaneColor() {
		return LANE_COLORS[0];
	}

	GitGraphRow row(int index) {
		return rows.get(index);
	}

	int size() {
		return rows.size();
	}

	int laneCount() {
		return laneCount;
	}

}
