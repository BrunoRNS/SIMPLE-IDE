package io.simpleide.ui.sidebar;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.List;
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
 * <p>
 * The layout is computed by {@link GitGraphLayout} and painted by
 * {@link GitGraphPainter}; this class only hosts the graph in the Source Control panel
 * and handles mouse interaction.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 */
public final class GitCommitGraphPanel extends JPanel {

	private GitGraphPainter painter = new GitGraphPainter(GitGraphLayout.empty());

	private Consumer<GitCommit> checkoutListener = commit -> {
	};

	public GitCommitGraphPanel() {
		setOpaque(true);
		setPreferredSize(new Dimension(500, GitGraphPainter.ROW_HEIGHT));

		MouseNode mouseNode = new MouseNode().onClick(event -> {
			GitCommit commit = commitAt(event.getY());

			if (commit != null && event.getClickCount() == 2) {
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

	public void setCommits(List<GitCommit> commits) {
		painter = new GitGraphPainter(GitGraphLayout.build(Objects.requireNonNull(commits, "commits")));

		setPreferredSize(new Dimension(painter.preferredWidth(), painter.contentHeight()));

		revalidate();
		repaint();
	}

	public void setCheckoutListener(Consumer<GitCommit> listener) {
		checkoutListener = listener == null ? commit -> {
		} : listener;
	}

	@Override
	public String getToolTipText(MouseEvent event) {
		GitCommit commit = commitAt(event.getY());

		return commit == null ? null : commit.hash() + " " + commit.message();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);

		Graphics2D g = (Graphics2D) graphics.create();

		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

			painter.paint(g, getWidth(), getBackground(), getForeground());
		}
		finally {
			g.dispose();
		}
	}

	private GitCommit commitAt(int y) {
		return painter.commitAt(y);
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

}
