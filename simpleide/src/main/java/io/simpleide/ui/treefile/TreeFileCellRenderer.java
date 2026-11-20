package io.simpleide.ui.treefile;

import java.awt.Color;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.icon.MaterialIcons;

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
 * @since v1.1.0
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
		Color treeBackground = ThemeColors.ui("Tree.background");
		Color selectionBackground = ThemeColors.ui("Tree.selectionBackground");
		Color treeForeground = ThemeColors.ui("Tree.foreground");
		Color selectionForeground = ThemeColors.ui("Tree.selectionForeground");
		if (tree != null) {
			treeBackground = treeBackground == null ? tree.getBackground() : treeBackground;
			treeForeground = treeForeground == null ? tree.getForeground() : treeForeground;
		}
		setBackgroundNonSelectionColor(treeBackground);
		setTextNonSelectionColor(treeForeground);
		setBackgroundSelectionColor(selectionBackground == null ? treeBackground : selectionBackground);
		setTextSelectionColor(selectionForeground == null ? treeForeground : selectionForeground);
		setBorderSelectionColor(selectionBackground == null ? treeBackground : selectionBackground);
		setForeground(selected ? selectionForeground : treeForeground);
		setOpaque(false);
	}

}
