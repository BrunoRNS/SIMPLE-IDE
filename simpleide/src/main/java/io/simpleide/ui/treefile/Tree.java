package io.simpleide.ui.treefile;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
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
	}

	/**
	 * Returns the selection and current-directory state.
	 * @return the tree state
	 */
	public TreeState getState() {
		return state;
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

}
