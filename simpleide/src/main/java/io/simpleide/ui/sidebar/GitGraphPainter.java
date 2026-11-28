package io.simpleide.ui.sidebar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Objects;

import io.simpleide.git.GitCommit;

/**
 * Paints the commit graph column, the commit nodes and the commit text.
 * <p>
 * Connections are drawn as short segments inside a single row: vertical lines for lanes
 * crossing a row, rounded curves when a lane changes column and a branch line when a
 * merge commit opens a lane for an extra parent.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 */
final class GitGraphPainter {

	static final int ROW_HEIGHT = 46;

	private static final int LANE_WIDTH = 18;

	private static final int GRAPH_PADDING = 14;

	private static final int TEXT_PADDING = 18;

	private static final int TEXT_WIDTH = 340;

	private static final float CONNECTION_STROKE = 2f;

	private static final Color SEPARATOR_COLOR = new Color(128, 128, 128, 35);

	private static final Color MUTED_COLOR = new Color(128, 128, 128);

	private final GitGraphLayout layout;

	GitGraphPainter(GitGraphLayout layout) {
		this.layout = Objects.requireNonNull(layout, "layout");
	}

	int preferredWidth() {
		return graphWidth() + TEXT_WIDTH;
	}

	int contentHeight() {
		return Math.max(ROW_HEIGHT, layout.size() * ROW_HEIGHT);
	}

	/**
	 * Finds the commit rendered at a vertical position.
	 * @param y panel-relative vertical position
	 * @return commit, or {@code null} when the position is outside the rows
	 */
	GitCommit commitAt(int y) {
		int index = y / ROW_HEIGHT;

		return index >= 0 && index < layout.size() ? layout.row(index).commit() : null;
	}

	/**
	 * Paints the whole graph.
	 * @param g graphics context
	 * @param width available width
	 * @param background panel background
	 * @param foreground panel foreground
	 */
	void paint(Graphics2D g, int width, Color background, Color foreground) {
		drawSeparators(g, width);

		for (int index = 0; index < layout.size(); index++) {
			drawRow(g, layout.row(index), index, width, background, foreground);
		}
	}

	private int graphWidth() {
		return GRAPH_PADDING + (layout.laneCount() + 1) * LANE_WIDTH + GRAPH_PADDING / 2;
	}

	private static int laneX(int lane) {
		return GRAPH_PADDING + (lane + 1) * LANE_WIDTH;
	}

	private static int rowTop(int index) {
		return index * ROW_HEIGHT;
	}

	private static int rowMiddle(int index) {
		return rowTop(index) + ROW_HEIGHT / 2;
	}

	private static int rowBottom(int index) {
		return rowTop(index) + ROW_HEIGHT;
	}

	private void drawSeparators(Graphics2D g, int width) {
		g.setColor(SEPARATOR_COLOR);

		for (int index = 1; index <= layout.size(); index++) {
			int y = index * ROW_HEIGHT;

			g.drawLine(0, y, width, y);
		}
	}

	private void drawRow(Graphics2D g, GitGraphRow row, int index, int width, Color background, Color foreground) {
		GitCommit commit = row.commit();
		List<GitGraphLane> input = row.input();
		List<GitGraphLane> output = row.output();

		int inputIndex = row.inputIndex();
		int circleIndex = row.circleIndex();

		Color circleColor = circleColor(row, circleIndex);

		int top = rowTop(index);
		int middle = rowMiddle(index);
		int bottom = rowBottom(index);

		g.setStroke(new BasicStroke(CONNECTION_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		int outputIndex = 0;

		for (int laneIndex = 0; laneIndex < input.size(); laneIndex++) {
			GitGraphLane lane = input.get(laneIndex);

			if (lane.id().equals(commit.hash())) {
				if (laneIndex != circleIndex) {
					// Duplicate lane heading to the current commit: connect it to the
					// commit node.
					Path2D path = new Path2D.Float();
					path.moveTo(laneX(laneIndex), top);
					path.quadTo(laneX(laneIndex), middle, laneX(laneIndex) - LANE_WIDTH, middle);
					path.lineTo(laneX(circleIndex), middle);

					stroke(g, path, lane.color());
				}
				else {
					outputIndex++;
				}

				continue;
			}

			if (outputIndex >= output.size() || !lane.id().equals(output.get(outputIndex).id())) {
				continue;
			}

			if (laneIndex == outputIndex) {
				stroke(g, verticalLine(laneX(laneIndex), top, bottom), lane.color());
			}
			else {
				stroke(g, laneShiftPath(laneX(laneIndex), laneX(outputIndex), top, middle, bottom), lane.color());
			}

			outputIndex++;
		}

		// Branch lines leaving a merge commit towards its extra parents.
		for (int parentIndex = 1; parentIndex < commit.parents().size(); parentIndex++) {
			int parentLane = row.lastOutputIndex(commit.parents().get(parentIndex));

			if (parentLane < 0) {
				continue;
			}

			Path2D path = new Path2D.Float();

			path.moveTo(laneX(circleIndex), middle);
			path.lineTo(laneX(parentLane) - LANE_WIDTH, middle);
			path.quadTo(laneX(parentLane) - LANE_WIDTH, bottom, laneX(parentLane), bottom);

			stroke(g, path, output.get(parentLane).color());
		}

		// Half line entering the commit node.
		if (inputIndex >= 0) {
			stroke(g, verticalLine(laneX(circleIndex), top, middle), input.get(inputIndex).color());
		}

		// Half line leaving the commit node towards its first parent.
		if (!commit.parents().isEmpty()) {
			stroke(g, verticalLine(laneX(circleIndex), middle, bottom), circleColor);
		}

		drawNode(g, commit, laneX(circleIndex), middle, circleColor, background);
		drawCommitText(g, commit, index, circleColor, width, foreground);
	}

	private static Color circleColor(GitGraphRow row, int circleIndex) {
		if (circleIndex < row.output().size()) {
			return row.output().get(circleIndex).color();
		}
		if (circleIndex < row.input().size()) {
			return row.input().get(circleIndex).color();
		}

		return GitGraphLayout.defaultLaneColor();
	}

	/**
	 * Draws a lane that moves horizontally while crossing the row.
	 */
	private static Path2D laneShiftPath(int startX, int endX, int top, int middle, int bottom) {
		int radius = Math.min(LANE_WIDTH / 2, Math.abs(endX - startX) / 2);
		int direction = endX >= startX ? 1 : -1;

		Path2D path = new Path2D.Float();

		path.moveTo(startX, top);
		path.lineTo(startX, middle - radius);
		path.quadTo(startX, middle, startX + direction * radius, middle);
		path.lineTo(endX - direction * radius, middle);
		path.quadTo(endX, middle, endX, middle + radius);
		path.lineTo(endX, bottom);

		return path;
	}

	private static Path2D verticalLine(int x, int startY, int endY) {
		Path2D path = new Path2D.Float();

		path.moveTo(x, startY);
		path.lineTo(x, endY);

		return path;
	}

	private static void stroke(Graphics2D g, Path2D path, Color color) {
		g.setColor(color);
		g.draw(path);
	}

	private static void drawNode(Graphics2D g, GitCommit commit, int x, int y, Color color, Color background) {
		boolean merge = commit.parents().size() > 1;

		// Ring separating the node from the lines running underneath it.
		g.setColor(background);
		g.fillOval(x - 8, y - 8, 16, 16);

		g.setColor(color);

		if (merge) {
			g.fillOval(x - 7, y - 7, 14, 14);
			g.setColor(background);
			g.fillOval(x - 3, y - 3, 6, 6);
		}
		else {
			g.fillOval(x - 5, y - 5, 10, 10);
		}
	}

	private void drawCommitText(Graphics2D g, GitCommit commit, int index, Color laneColor, int width,
			Color foreground) {
		int x = graphWidth() + TEXT_PADDING;
		int y = index * ROW_HEIGHT;

		int availableWidth = width - x - TEXT_PADDING;

		if (availableWidth <= 0) {
			return;
		}

		Font originalFont = g.getFont();
		FontMetrics metrics;

		// Commit message
		g.setFont(originalFont.deriveFont(Font.BOLD, 12f));
		g.setColor(foreground);

		metrics = g.getFontMetrics();

		String message = truncate(commit.message().replace('\n', ' '), metrics, availableWidth);

		g.drawString(message, x, y + 19);

		// Commit metadata, followed by the branch and tag references on the same line
		g.setFont(originalFont.deriveFont(Font.PLAIN, 11f));
		g.setColor(MUTED_COLOR);

		metrics = g.getFontMetrics();

		int referencesWidth = Math.min(referencesWidth(metrics, commit), availableWidth / 2);
		int gap = referencesWidth > 0 ? 10 : 0;

		String metadata = commit.abbreviatedHash() + "  ·  " + commit.author();
		String metadataText = truncate(metadata, metrics, Math.max(0, availableWidth - referencesWidth - gap));

		g.drawString(metadataText, x, y + 34);

		if (referencesWidth > 0) {
			int referencesX = x + metrics.stringWidth(metadataText) + gap;

			drawReferences(g, commit, laneColor, referencesX, y + 34, referencesWidth);
		}

		g.setFont(originalFont);
	}

	private static int referencesWidth(FontMetrics metrics, GitCommit commit) {
		int width = 0;

		for (String ref : commit.refs()) {
			width += metrics.stringWidth(shortRef(ref)) + 14 + 5;
		}

		return width;
	}

	private void drawReferences(Graphics2D g, GitCommit commit, Color laneColor, int x, int y, int availableWidth) {
		int currentX = x;

		for (String ref : commit.refs()) {
			String label = shortRef(ref);

			FontMetrics metrics = g.getFontMetrics();
			int badgeWidth = metrics.stringWidth(label) + 14;

			if (currentX + badgeWidth - x > availableWidth) {
				break;
			}

			g.setColor(new Color(laneColor.getRed(), laneColor.getGreen(), laneColor.getBlue(), 35));

			g.fillRoundRect(currentX, y - 10, badgeWidth, 15, 7, 7);

			g.setColor(laneColor);
			g.drawString(label, currentX + 7, y);

			currentX += badgeWidth + 5;
		}
	}

	private static String shortRef(String ref) {
		return ref.replace("refs/heads/", "").replace("refs/remotes/", "").replace("refs/tags/", "");
	}

	private static String truncate(String text, FontMetrics metrics, int maximumWidth) {
		if (metrics.stringWidth(text) <= maximumWidth) {
			return text;
		}

		String suffix = "...";
		int availableWidth = maximumWidth - metrics.stringWidth(suffix);

		if (availableWidth <= 0) {
			return suffix;
		}

		int end = text.length();

		while (end > 0 && metrics.stringWidth(text.substring(0, end)) > availableWidth) {
			end--;
		}

		return text.substring(0, end) + suffix;
	}

}
