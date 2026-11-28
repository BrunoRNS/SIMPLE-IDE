package io.simpleide.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.ui.treefile.Tree;

/**
 * Explorer view hosting the project tree.
 *
 * <p>
 * The view shows a centered {@code No folder opened} message until a folder is selected.
 * The tree itself is supplied by the frame, which owns its watcher and selection
 * behavior.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ExplorerPanel extends JPanel {

	private static final String EMPTY_CARD = "empty";

	private static final String TREE_CARD = "tree";

	private final CardLayout cards = new CardLayout();

	private final JPanel content = new JPanel(cards);

	private final JScrollPane treePane;

	private final JPanel emptyState = new JPanel();

	/**
	 * Creates an explorer that starts without a folder.
	 * @param treePane scroll pane that will host the project tree
	 * @param openFolder action that opens the folder chooser
	 */
	public ExplorerPanel(JScrollPane treePane, Runnable openFolder) {
		super();
		this.treePane = treePane;
		setLayout(new BorderLayout());
		JLabel title = new JLabel("EXPLORER");
		title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		Font titleFont = UIManager.getFont("Label.font");
		if (titleFont != null) {
			title.setFont(titleFont.deriveFont(Font.BOLD, Math.max(10f, titleFont.getSize2D() - 1f)));
		}
		add(title, BorderLayout.NORTH);

		createEmptyState(openFolder);
		content.add(emptyState, EMPTY_CARD);
		content.add(treePane, TREE_CARD);
		add(content, BorderLayout.CENTER);
		showNoFolder();
	}

	private void createEmptyState(Runnable openFolder) {
		emptyState.setLayout(new BoxLayout(emptyState, BoxLayout.Y_AXIS));
		emptyState.setBorder(BorderFactory.createEmptyBorder(24, 20, 24, 20));

		JLabel title = new JLabel("No folder selected");
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		Font titleFont = UIManager.getFont("Label.font");
		if (titleFont != null) {
			title.setFont(titleFont.deriveFont(Font.BOLD, titleFont.getSize2D() + 1f));
		}

		JLabel description = new JLabel(
				"<html><div style='text-align:center'>Open a project folder to browse and edit its files.</div></html>");
		description.setHorizontalAlignment(JLabel.CENTER);
		description.setAlignmentX(Component.CENTER_ALIGNMENT);

		JButton openButton = new JButton("Open folder");
		openButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		openButton.getAccessibleContext().setAccessibleName("Open project folder");
		openButton.addActionListener(new ActionNode(event -> openFolder.run()));

		emptyState.add(Box.createVerticalGlue());
		emptyState.add(title);
		emptyState.add(Box.createVerticalStrut(8));
		emptyState.add(description);
		emptyState.add(Box.createVerticalStrut(16));
		emptyState.add(openButton);
		emptyState.add(Box.createVerticalGlue());
	}

	/**
	 * Shows the {@code No folder opened} state.
	 */
	public void showNoFolder() {
		cards.show(content, EMPTY_CARD);
	}

	/**
	 * Shows a project tree in the explorer.
	 * @param tree tree to display
	 */
	public void showTree(Tree tree) {
		treePane.setViewportView(tree);
		treePane.revalidate();
		treePane.repaint();
		cards.show(content, TREE_CARD);
	}

}
