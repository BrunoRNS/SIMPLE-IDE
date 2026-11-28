package io.simpleide.ui.treefile;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class TreeFileTransferHandler extends TransferHandler {

	private final Tree tree;

	TreeFileTransferHandler(Tree tree) {
		this.tree = tree;
	}

	@Override
	public int getSourceActions(JComponent c) {
		return DnDConstants.ACTION_COPY_OR_MOVE;
	}

	@Override
	public Transferable createTransferable(JComponent c) {
		TreePath selectionPath = tree.getSelectionPath();
		if (selectionPath == null) {
			return null;
		}
		Object userObject = ((DefaultMutableTreeNode) selectionPath.getLastPathComponent()).getUserObject();
		if (!(userObject instanceof TreeFile file)) {
			return null;
		}
		return new TreeFileTransferable(List.of(file));
	}

	@Override
	public void exportDone(JComponent source, Transferable data, int action) {
		if (action == DnDConstants.ACTION_MOVE) {
			// The drop target listener handles the actual move
		}
	}

}
