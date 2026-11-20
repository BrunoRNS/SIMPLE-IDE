package io.simpleide.ui.treefile;

import io.simpleide.ui.icon.MaterialIcons;
import java.awt.Color;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Tree cell renderer that draws folders and per-language file icons.
 *
 * <p>
 * Icons come from the bundled Material Icon Theme manifest, matched by file name,
 * compound extension or extension, with a default file icon when no mapping exists.
 * Foreground and selection colours are taken from the active look and feel and adjusted
 * when necessary so names remain readable on every theme.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class TreeFileCellRenderer extends DefaultTreeCellRenderer {

	private static final long serialVersionUID = 1L;

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
			boolean leaf, int row, boolean hasFocus) {
		super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
		if (tree != null) {
			setFont(tree.getFont());
		}
		applyTextColors(tree, selected);
		if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof TreeFile file) {
			Icon icon = file instanceof Directory directory ? MaterialIcons.forDirectory(directory.getPath(), expanded)
					: MaterialIcons.forFile(file.getPath());
			setIcon(icon);
		}
		return this;
	}

	private void applyTextColors(JTree tree, boolean selected) {
		Color background = selected ? UIManager.getColor("Tree.selectionBackground")
				: UIManager.getColor("Tree.background");
		Color foreground = selected ? UIManager.getColor("Tree.selectionForeground")
				: UIManager.getColor("Tree.foreground");
		if (background == null && tree != null) {
			background = tree.getBackground();
		}
		if (foreground == null && tree != null) {
			foreground = tree.getForeground();
		}
		setOpaque(selected);
		if (background != null) {
			setBackground(background);
		}
		if (foreground != null) {
			setForeground(foreground);
		}
	}

}
