package io.simpleide.ui.sidebar;

import io.simpleide.ui.treefile.Tree;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

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

	private final JLabel emptyLabel = new JLabel("No folder opened");

	/**
	 * Creates an explorer that starts without a folder.
	 * @param treePane scroll pane that will host the project tree
	 */
	public ExplorerPanel(JScrollPane treePane) {
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

		emptyLabel.setHorizontalAlignment(JLabel.CENTER);
		emptyLabel.setBorder(BorderFactory.createEmptyBorder(24, 12, 24, 12));
		content.add(emptyLabel, EMPTY_CARD);
		content.add(treePane, TREE_CARD);
		add(content, BorderLayout.CENTER);
		showNoFolder();
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
