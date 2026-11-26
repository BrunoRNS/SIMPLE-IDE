package io.simpleide.ui.sidebar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import io.simpleide.git.GitCommit;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.edt.listeners.MouseNode;

/**
 * Swing commit graph backed by JGit commit metadata.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class GitCommitGraphPanel extends JPanel {

	private static final int ROW_HEIGHT = 42;

	private static final Color[] LANE_COLORS = { new Color(86, 156, 214), new Color(198, 120, 221),
			new Color(152, 195, 121), new Color(229, 192, 123), new Color(224, 108, 117), new Color(86, 182, 194) };

	private List<GitCommit> commits = List.of();

	private Consumer<GitCommit> checkoutListener = commit -> {
	};

	/**
	 * Creates an empty commit graph.
	 */
	public GitCommitGraphPanel() {
		setPreferredSize(new Dimension(0, ROW_HEIGHT));
		MouseNode mouseNode = new MouseNode().onClick(event -> {
			GitCommit commit = commitAt(event.getY());
			if (commit == null) {
				return;
			}
			if (event.getClickCount() == 2) {
				showDetails(commit);
			}
		}).onPress(event -> {
			if (event.isPopupTrigger()) {
				showCommitMenu(event);
			}
		}).onRelease(event -> {
			if (event.isPopupTrigger()) {
				showCommitMenu(event);
			}
		});
		addMouseListener(mouseNode);
	}

	/**
	 * Replaces the commits shown in the graph.
	 * @param commits commit metadata
	 */
	public void setCommits(List<GitCommit> commits) {
		this.commits = List.copyOf(Objects.requireNonNull(commits, "commits"));
		int laneCount = commits.stream().mapToInt(commit -> commit.lane() + 1).max().orElse(1);
		setPreferredSize(new Dimension(Math.max(240, 24 + laneCount * 20 + 190),
				Math.max(ROW_HEIGHT, commits.size() * ROW_HEIGHT)));
		revalidate();
		repaint();
	}

	/**
	 * Registers a listener invoked when the user chooses checkout.
	 * @param listener checkout listener
	 */
	public void setCheckoutListener(Consumer<GitCommit> listener) {
		checkoutListener = listener == null ? commit -> {
		} : listener;
	}

	private GitCommit commitAt(int y) {
		int index = y / ROW_HEIGHT;
		return index >= 0 && index < commits.size() ? commits.get(index) : null;
	}

	@Override
	public String getToolTipText(MouseEvent event) {
		GitCommit commit = commitAt(event.getY());
		return commit == null ? null : commit.hash() + " " + commit.message();
	}

	private void showDetails(GitCommit commit) {
		JOptionPane.showMessageDialog(this, "Hash: " + commit.hash() + "\nAuthor: " + commit.author() + "\nDate: "
				+ commit.date() + "\n\n" + commit.message(), "Commit Details", JOptionPane.INFORMATION_MESSAGE);
	}

	private void showCommitMenu(MouseEvent event) {
		GitCommit commit = commitAt(event.getY());
		if (commit == null) {
			return;
		}
		JPopupMenu menu = new JPopupMenu();
		JMenuItem details = new JMenuItem("Show commit details");
		details.addActionListener(new ActionNode(action -> showDetails(commit)));
		JMenuItem checkout = new JMenuItem("Checkout commit");
		checkout.addActionListener(new ActionNode(action -> checkoutListener.accept(commit)));
		menu.add(details);
		menu.add(checkout);
		menu.show(this, event.getX(), event.getY());
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(new BasicStroke(2f));
			Map<String, Integer> commitRows = new HashMap<>();
			for (int index = 0; index < commits.size(); index++) {
				commitRows.put(commits.get(index).hash(), index);
			}
			for (int index = 0; index < commits.size(); index++) {
				GitCommit commit = commits.get(index);
				int x = laneX(commit.lane());
				int y = index * ROW_HEIGHT + ROW_HEIGHT / 2;
				g.setColor(laneColor(commit.lane()));
				for (String parent : commit.parents()) {
					Integer parentIndex = commitRows.get(parent);
					if (parentIndex != null) {
						GitCommit parentCommit = commits.get(parentIndex);
						int parentY = parentIndex * ROW_HEIGHT + ROW_HEIGHT / 2;
						g.drawLine(x, y, laneX(parentCommit.lane()), parentY);
					}
				}
			}
			for (int index = 0; index < commits.size(); index++) {
				GitCommit commit = commits.get(index);
				int x = laneX(commit.lane());
				int y = index * ROW_HEIGHT + ROW_HEIGHT / 2;
				g.setColor(laneColor(commit.lane()));
				g.fillOval(x - 7, y - 7, 14, 14);
				g.setColor(getForeground());
				String refs = commit.refs().isEmpty() ? "" : "  [" + String.join(", ", commit.refs()) + "]";
				String summary = commit.abbreviatedHash() + "  " + commit.message() + refs;
				g.drawString(summary, Math.min(x + 14, Math.max(x + 14, getWidth() - 190)), y - 2);
				g.setFont(g.getFont().deriveFont(11f));
				g.drawString(commit.author(), Math.min(x + 14, Math.max(x + 14, getWidth() - 190)), y + 14);
			}
		}
		finally {
			g.dispose();
		}
	}

	private static int laneX(int lane) {
		return 18 + Math.max(0, lane) * 20;
	}

	private static Color laneColor(int lane) {
		return LANE_COLORS[Math.floorMod(lane, LANE_COLORS.length)];
	}

}
