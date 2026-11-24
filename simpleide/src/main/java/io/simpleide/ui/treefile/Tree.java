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

import javax.swing.AbstractAction;
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
import io.simpleide.thread.nodes.edt.listeners.TreeSelectionNode;
import io.simpleide.thread.nodes.edt.listeners.TreeWillExpandNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * Swing tree that displays and edits a project directory hierarchy.
 *
 * <p>
 * Directories load their children lazily when expanded, and a watcher can refresh them
 * when the file system changes. The tree installs the context menu from
 * {@link TreeFileActions} and keeps a {@link TreeState} with the selection and current
 * directory. Root changes are marshalled to the Swing Event Dispatch Thread.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class Tree extends JTree {

	private static final Logger LOGGER = LoggerFactory.getLogger(Tree.class);

	private final TreeState state;

	private final Set<Directory> loadingDirectories = Collections.newSetFromMap(new IdentityHashMap<>());

	private final Set<Path> refreshingDirectories = new HashSet<>();

	private final TreeFileActions fileActions;

	private TreeFileDropTargetListener dropTargetListener;

	private Consumer<Directory> rootChangeListener = root -> {
	};

	@Override
	public void updateUI() {
		super.updateUI();
		Color background = UIManager.getColor("Tree.background");
		Color foreground = UIManager.getColor("Tree.foreground");
		if (background != null) {
			setBackground(background);
		}
		if (foreground != null) {
			setForeground(foreground);
		}
	}

	/**
	 * Creates a tree for a root directory.
	 * @param root directory shown at the root of the tree
	 */
	public Tree(Directory root) {
		super(createNode(root));
		setCellRenderer(new TreeFileCellRenderer());
		state = new TreeState(root);
		fileActions = new TreeFileActions(this, state);
		addTreeSelectionListener(new TreeSelectionNode(event -> updateState(event.getNewLeadSelectionPath())));
		fileActions.installContextMenu();
		LOGGER.info("Tree state initialized: currentDirectory={}", root.getPath());
		addTreeWillExpandListener(new TreeWillExpandNode().onExpand(event -> loadChildren(event.getPath())));
		installKeyboardActions();
		installDragAndDrop();
	}

	private void installKeyboardActions() {
		getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK),
				"copy");
		getActionMap().put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				fileActions.copySelected();
			}
		});

		getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK), "cut");
		getActionMap().put("cut", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				fileActions.cutSelected();
			}
		});

		getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK),
				"paste");
		getActionMap().put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				fileActions.pasteSelected();
			}
		});
	}

	private void installDragAndDrop() {
		setTransferHandler(new TreeFileTransferHandler(this));
		dropTargetListener = new TreeFileDropTargetListener(this);
		// Swing drag-and-drop needs a display. Headless tests exercise the drop
		// listener directly so the transfer logic stays covered without one.
		if (!GraphicsEnvironment.isHeadless()) {
			setDragEnabled(true);
			setDropTarget(new DropTarget(this, dropTargetListener));
		}
	}

	TreeFileDropTargetListener getDropTargetListener() {
		return dropTargetListener;
	}

	/**
	 * Returns the selection and current-directory state.
	 * @return the tree state
	 */
	public TreeState getState() {
		return state;
	}

	/**
	 * Returns the file actions for testing purposes.
	 * @return the file actions
	 */
	TreeFileActions getFileActions() {
		return fileActions;
	}

	/**
	 * Registers a listener notified when the tree root changes.
	 * @param listener root listener, or {@code null} to clear it
	 */
	public void setRootChangeListener(Consumer<Directory> listener) {
		rootChangeListener = listener == null ? root -> {
		} : listener;
	}

	/**
	 * Replaces the tree root, switching to the EDT when necessary.
	 * @param root new root directory
	 * @throws IllegalArgumentException when the root is {@code null}
	 */
	public void setRoot(Directory root) {
		if (root == null) {
			throw new IllegalArgumentException("Tree root must not be null.");
		}

		if (SwingUtilities.isEventDispatchThread()) {
			setRootOnEDT(root);
			return;
		}
		ThreadMaster.getInstance().executeOnEDT(() -> setRootOnEDT(root));
	}

	private void setRootOnEDT(Directory root) {
		DefaultMutableTreeNode rootNode = createNode(root);
		setModel(new DefaultTreeModel(rootNode));
		state.setRootDirectory(root);
		setSelectionPath(new TreePath(rootNode.getPath()));
		LOGGER.info("Tree root updated: {}", root.getPath());
		rootChangeListener.accept(root);
	}

	/**
	 * Re-reads a directory from disk and updates its children in the tree.
	 * @param path directory to refresh
	 */
	public void refreshDirectory(Path path) {
		if (path == null) {
			return;
		}

		Path normalizedPath = path.toAbsolutePath().normalize();
		if (!SwingUtilities.isEventDispatchThread()) {
			ThreadMaster.getInstance().executeOnEDT(() -> refreshDirectory(normalizedPath));
			return;
		}

		DefaultMutableTreeNode node = findDirectoryNode(normalizedPath);
		if (node == null || !(node.getUserObject() instanceof Directory directory) || !directory.areChildrenLoaded()
				|| !refreshingDirectories.add(normalizedPath)) {
			return;
		}

		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					List<TreeFile> children = directory.readChildrenFromDisk();
					threadMaster.executeOnEDT(() -> {
						refreshingDirectories.remove(normalizedPath);
						if (node.getRoot() != getModel().getRoot()) {
							return;
						}
						Path selectedPath = getSelectedPath();
						Map<Path, DefaultMutableTreeNode> existingNodes = getChildNodes(node);
						List<TreeFile> updatedChildren = new java.util.ArrayList<>(children.size());
						List<DefaultMutableTreeNode> updatedNodes = new java.util.ArrayList<>(children.size());
						for (TreeFile child : children) {
							Path childPath = child.getPath().toAbsolutePath().normalize();
							DefaultMutableTreeNode existingNode = existingNodes.get(childPath);
							if (child instanceof Directory && existingNode != null
									&& existingNode.getUserObject() instanceof Directory existingDirectory) {
								updatedChildren.add(existingDirectory);
								updatedNodes.add(existingNode);
							}
							else {
								updatedChildren.add(child);
								updatedNodes.add(createNode(child));
							}
						}

						directory.replaceChildren(updatedChildren);
						node.removeAllChildren();
						for (DefaultMutableTreeNode childNode : updatedNodes) {
							node.add(childNode);
						}
						((DefaultTreeModel) getModel()).nodeStructureChanged(node);
						if (selectedPath != null && selectedPath.startsWith(normalizedPath)) {
							DefaultMutableTreeNode selectedNode = findTreeFileNode(selectedPath);
							if (selectedNode == null) {
								selectedNode = node;
							}
							setSelectionPath(new TreePath(selectedNode.getPath()));
						}
					});
				}
				catch (IOException error) {
					LOGGER.error("Unable to refresh directory {}.", normalizedPath, error);
					threadMaster.executeOnEDT(() -> refreshingDirectories.remove(normalizedPath));
				}
			}));
		}
		catch (RuntimeException error) {
			refreshingDirectories.remove(normalizedPath);
			LOGGER.error("Unable to schedule directory refresh for {}.", normalizedPath, error);
		}
	}

	private DefaultMutableTreeNode findDirectoryNode(Path path) {
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<?> nodes = root.depthFirstEnumeration();
		while (nodes.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
			if (node.getUserObject() instanceof Directory directory
					&& directory.getPath().toAbsolutePath().normalize().equals(path)) {
				return node;
			}
		}
		return null;
	}

	private Map<Path, DefaultMutableTreeNode> getChildNodes(DefaultMutableTreeNode parent) {
		Map<Path, DefaultMutableTreeNode> children = new HashMap<>();
		for (int index = 0; index < parent.getChildCount(); index++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(index);
			if (child.getUserObject() instanceof TreeFile file) {
				children.put(file.getPath().toAbsolutePath().normalize(), child);
			}
		}
		return children;
	}

	private Path getSelectedPath() {
		TreePath selected = getSelectionPath();
		if (selected == null) {
			return null;
		}
		Object userObject = ((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject();
		return userObject instanceof TreeFile file ? file.getPath().toAbsolutePath().normalize() : null;
	}

	private DefaultMutableTreeNode findTreeFileNode(Path path) {
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
		Enumeration<?> nodes = root.depthFirstEnumeration();
		while (nodes.hasMoreElements()) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
			if (node.getUserObject() instanceof TreeFile file
					&& file.getPath().toAbsolutePath().normalize().equals(path)) {
				return node;
			}
		}
		return null;
	}

	private void updateState(TreePath path) {
		if (path == null) {
			state.select(null, null);
			logState();
			return;
		}

		DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
		if (!(node.getUserObject() instanceof TreeFile selected)) {
			return;
		}
		Directory parent = node.getParent() instanceof DefaultMutableTreeNode parentNode
				? (Directory) parentNode.getUserObject() : null;
		state.select(selected, parent);
		logState();
	}

	private void logState() {
		TreeFile selected = state.getSelected();
		LOGGER.info("Tree state updated: selected={}, currentDirectory={}",
				selected == null ? "none" : selected.getPath(), state.getCurrentDirectory().getPath());
	}

	private void loadChildren(TreePath path) {
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
		if (!(node.getUserObject() instanceof Directory directory) || directory.areChildrenLoaded()
				|| !loadingDirectories.add(directory)) {
			return;
		}

		directory.setLoading(true);
		node.removeAllChildren();
		node.add(new DefaultMutableTreeNode("Loading..."));
		((DefaultTreeModel) getModel()).nodeStructureChanged(node);

		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					directory.loadChildren();
					threadMaster.executeOnEDT(() -> {
						loadingDirectories.remove(directory);
						directory.setLoading(false);
						if (node.getRoot() != getModel().getRoot()) {
							return;
						}
						node.removeAllChildren();
						for (TreeFile child : directory.getChildren()) {
							node.add(createNode(child));
						}
						((DefaultTreeModel) getModel()).nodeStructureChanged(node);
					});
				}
				catch (IOException exception) {
					LOGGER.error("Unable to load directory {}.", directory.getPath(), exception);
					threadMaster.executeOnEDT(() -> {
						loadingDirectories.remove(directory);
						directory.setLoading(false);
						if (node.getRoot() == getModel().getRoot()) {
							node.removeAllChildren();
							node.add(new DefaultMutableTreeNode("Unable to load; collapse and expand to retry"));
							((DefaultTreeModel) getModel()).nodeStructureChanged(node);
						}
					});
				}
			}));
		}
		catch (RuntimeException exception) {
			loadingDirectories.remove(directory);
			directory.setLoading(false);
			LOGGER.error("Unable to schedule loading directory {}.", directory.getPath(), exception);
			node.removeAllChildren();
			node.add(new DefaultMutableTreeNode());
			((DefaultTreeModel) getModel()).nodeStructureChanged(node);
		}
	}

	static DefaultMutableTreeNode createNode(TreeFile file) {
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(file);
		if (file instanceof Directory directory) {
			if (directory.areChildrenLoaded()) {
				for (TreeFile child : directory.getChildren()) {
					node.add(createNode(child));
				}
			}
			else {
				node.add(new DefaultMutableTreeNode());
			}
		}
		return node;
	}

	/**
	 * Starts the new-file action in the current directory.
	 */
	public void createNewFile() {
		fileActions.createNewFile();
	}

	/**
	 * Starts the new-folder action in the current directory.
	 */
	public void createNewDirectory() {
		fileActions.createNewDirectory();
	}

	/**
	 * Refreshes a directory node in the tree model.
	 * @param directory the directory to refresh
	 */
	void refreshDirectoryNode(Directory directory) {
		fileActions.refreshDirectoryNode(directory);
	}

}

/**
 * Transfer handler for drag-and-drop operations on the file tree.
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

/**
 * Transferable for TreeFile entries.
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

/**
 * Drop target listener for handling drops on the file tree.
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
