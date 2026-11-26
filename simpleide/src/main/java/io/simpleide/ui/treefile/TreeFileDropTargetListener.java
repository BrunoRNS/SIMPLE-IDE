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
final class TreeFileDropTargetListener implements DropTargetListener {

	private static final Logger LOGGER = LoggerFactory.getLogger(TreeFileDropTargetListener.class);

	private final Tree tree;

	TreeFileDropTargetListener(Tree tree) {
		this.tree = tree;
	}

	@Override
	public void dragEnter(DropTargetDragEvent dtde) {
		if (isValidDrop(dtde)) {
			dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
		}
		else {
			dtde.rejectDrag();
		}
	}

	@Override
	public void dragOver(DropTargetDragEvent dtde) {
		if (isValidDrop(dtde)) {
			dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
		}
		else {
			dtde.rejectDrag();
		}
	}

	@Override
	public void dropActionChanged(DropTargetDragEvent dtde) {
		dragOver(dtde);
	}

	@Override
	public void dragExit(DropTargetEvent dte) {
	}

	@Override
	public void drop(DropTargetDropEvent dtde) {
		if (!isValidDrop(dtde)) {
			dtde.rejectDrop();
			return;
		}

		try {
			Transferable transferable = dtde.getTransferable();
			if (transferable.isDataFlavorSupported(TreeFileTransferable.TREE_FILE_FLAVOR)) {
				@SuppressWarnings("unchecked")
				List<TreeFile> files = (List<TreeFile>) transferable
					.getTransferData(TreeFileTransferable.TREE_FILE_FLAVOR);
				TreePath dropPath = tree.getPathForLocation(dtde.getLocation().x, dtde.getLocation().y);
				Directory targetDirectory = getTargetDirectory(dropPath);
				if (targetDirectory == null) {
					dtde.rejectDrop();
					return;
				}

				boolean isMove = (dtde.getDropAction() & DnDConstants.ACTION_MOVE) != 0;
				boolean success = performDrop(files, targetDirectory, isMove);
				if (success) {
					dtde.acceptDrop(dtde.getDropAction());
				}
				else {
					dtde.rejectDrop();
				}
			}
			else {
				dtde.rejectDrop();
			}
		}
		catch (Exception exception) {
			LOGGER.error("Drop failed.", exception);
			dtde.rejectDrop();
		}
	}

	private boolean isValidDrop(DropTargetDragEvent dtde) {
		return dtde.isDataFlavorSupported(TreeFileTransferable.TREE_FILE_FLAVOR);
	}

	private boolean isValidDrop(DropTargetDropEvent dtde) {
		return dtde.isDataFlavorSupported(TreeFileTransferable.TREE_FILE_FLAVOR);
	}

	Directory getTargetDirectory(TreePath dropPath) {
		if (dropPath == null) {
			return tree.getState().getCurrentDirectory();
		}
		Object userObject = ((DefaultMutableTreeNode) dropPath.getLastPathComponent()).getUserObject();
		if (userObject instanceof Directory directory) {
			return directory;
		}
		if (userObject instanceof TreeFile file && dropPath.getParentPath() != null) {
			Object parentObject = ((DefaultMutableTreeNode) dropPath.getParentPath().getLastPathComponent())
				.getUserObject();
			if (parentObject instanceof Directory directory) {
				return directory;
			}
		}
		return tree.getState().getCurrentDirectory();
	}

	boolean performDrop(List<TreeFile> files, Directory targetDirectory, boolean isMove) {
		try {
			targetDirectory.loadChildren();
			for (TreeFile file : files) {
				Path sourcePath = file.getPath();
				Path targetPath = targetDirectory.getPath().resolve(sourcePath.getFileName());
				if (Files.exists(targetPath)) {
					return false;
				}
				if (isMove) {
					Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
				}
				else {
					copyRecursively(sourcePath, targetPath);
				}
			}
			targetDirectory.refreshChildren();
			tree.refreshDirectoryNode(targetDirectory);
			return true;
		}
		catch (IOException exception) {
			LOGGER.error("Drop failed.", exception);
			return false;
		}
	}

	private void copyRecursively(Path source, Path target) throws IOException {
		if (Files.isDirectory(source)) {
			Files.createDirectory(target);
			try (var stream = Files.list(source)) {
				stream.forEach(child -> {
					try {
						copyRecursively(child, target.resolve(child.getFileName()));
					}
					catch (IOException exception) {
						throw new RuntimeException(exception);
					}
				});
			}
		}
		else {
			Files.copy(source, target);
		}
	}

}
