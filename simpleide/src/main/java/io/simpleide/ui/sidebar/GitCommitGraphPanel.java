package io.simpleide.ui.sidebar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import io.simpleide.git.GitCommit;

/**
 * Swing commit graph backed by JGit commit metadata.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class GitCommitGraphPanel extends JPanel {

	private static final int ROW_HEIGHT = 42;

	private List<GitCommit> commits = List.of();

	private Consumer<GitCommit> checkoutListener = commit -> {
	};

	/**
	 * Creates an empty commit graph.
	 */
	public GitCommitGraphPanel() {
		setPreferredSize(new Dimension(0, ROW_HEIGHT));
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				GitCommit commit = commitAt(event.getY());
				if (commit == null) {
					return;
				}
				if (event.getClickCount() == 2) {
					showDetails(commit);
				}
			}

			@Override
			public void mousePressed(MouseEvent event) {
				if (event.isPopupTrigger()) {
					showCommitMenu(event);
				}
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				if (event.isPopupTrigger()) {
					showCommitMenu(event);
				}
			}
		});
	}

	/**
	 * Replaces the commits shown in the graph.
	 * @param commits commit metadata
	 */
	public void setCommits(List<GitCommit> commits) {
		this.commits = List.copyOf(Objects.requireNonNull(commits, "commits"));
		setPreferredSize(new Dimension(0, Math.max(ROW_HEIGHT, commits.size() * ROW_HEIGHT)));
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
		details.addActionListener(action -> showDetails(commit));
		JMenuItem checkout = new JMenuItem("Checkout commit");
		checkout.addActionListener(action -> checkoutListener.accept(commit));
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
			for (int index = 0; index < commits.size(); index++) {
				int y = index * ROW_HEIGHT + ROW_HEIGHT / 2;
				g.setColor(new Color(86, 156, 214));
				g.drawLine(18, y, 18, index == commits.size() - 1 ? y : y + ROW_HEIGHT);
				g.setColor(Color.ORANGE);
				g.fillOval(11, y - 7, 14, 14);

				GitCommit commit = commits.get(index);
				g.setColor(getForeground());
				g.drawString(commit.abbreviatedHash() + "  " + commit.message(), 34, y - 2);
				g.setFont(g.getFont().deriveFont(11f));
				g.drawString(commit.author(), 34, y + 14);
			}
		}
		finally {
			g.dispose();
		}
	}

}
