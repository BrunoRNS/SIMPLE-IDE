package io.simpleide.ui.treefile;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.SwingActionNode;
import io.simpleide.thread.nodes.edt.listeners.TreeSelectionNode;
import io.simpleide.thread.nodes.edt.listeners.TreeWillExpandNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class TreeFileTransferable implements Transferable {

	static final DataFlavor TREE_FILE_FLAVOR = new DataFlavor(TreeFile.class, "TreeFile");

	private final List<TreeFile> files;

	TreeFileTransferable(List<TreeFile> files) {
		this.files = files;
	}

	@Override
	public DataFlavor[] getTransferDataFlavors() {
		return new DataFlavor[] { TREE_FILE_FLAVOR };
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor) {
		return TREE_FILE_FLAVOR.equals(flavor);
	}

	@Override
	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
		if (!isDataFlavorSupported(flavor)) {
			throw new UnsupportedFlavorException(flavor);
		}
		return files;
	}

}
