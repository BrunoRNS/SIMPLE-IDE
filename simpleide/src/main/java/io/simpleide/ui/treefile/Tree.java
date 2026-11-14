package io.simpleide.ui.treefile;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
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
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tree extends JTree {
  private static final Logger LOGGER = LoggerFactory.getLogger(Tree.class);

  private final TreeState state;
  private final Set<Directory> loadingDirectories =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private final Set<Path> refreshingDirectories = new HashSet<>();
  private final TreeFileActions fileActions;
  private Consumer<Directory> rootChangeListener = root -> {};

  public Tree(Directory root) {
    super(createNode(root));
    setCellRenderer(new TreeFileCellRenderer());
    state = new TreeState(root);
    fileActions = new TreeFileActions(this, state);
    addTreeSelectionListener(event -> updateState(getSelectionPath()));
    fileActions.installContextMenu();
    LOGGER.info("Tree state initialized: currentDirectory={}", root.getPath());
    addTreeWillExpandListener(
        new TreeWillExpandListener() {
          @Override
          public void treeWillExpand(TreeExpansionEvent event) {
            loadChildren(event.getPath());
          }

          @Override
          public void treeWillCollapse(TreeExpansionEvent event) {}
        });
  }

  public TreeState getState() {
    return state;
  }

  public void setRootChangeListener(Consumer<Directory> listener) {
    rootChangeListener = listener == null ? root -> {} : listener;
  }

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
    if (node == null
        || !(node.getUserObject() instanceof Directory directory)
        || !directory.areChildrenLoaded()
        || !refreshingDirectories.add(normalizedPath)) {
      return;
    }

    ThreadMaster threadMaster = ThreadMaster.getInstance();
    try {
      threadMaster.register(
          new RunnableNode(
              () -> {
                try {
                  List<TreeFile> children = directory.readChildrenFromDisk();
                  threadMaster.executeOnEDT(
                      () -> {
                        refreshingDirectories.remove(normalizedPath);
                        if (node.getRoot() != getModel().getRoot()) {
                          return;
                        }
                        Path selectedPath = getSelectedPath();
                        Map<Path, DefaultMutableTreeNode> existingNodes = getChildNodes(node);
                        List<TreeFile> updatedChildren = new java.util.ArrayList<>(children.size());
                        List<DefaultMutableTreeNode> updatedNodes =
                            new java.util.ArrayList<>(children.size());
                        for (TreeFile child : children) {
                          Path childPath = child.getPath().toAbsolutePath().normalize();
                          DefaultMutableTreeNode existingNode = existingNodes.get(childPath);
                          if (child instanceof Directory
                              && existingNode != null
                              && existingNode.getUserObject()
                                  instanceof Directory existingDirectory) {
                            updatedChildren.add(existingDirectory);
                            updatedNodes.add(existingNode);
                          } else {
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
                } catch (IOException error) {
                  LOGGER.error("Unable to refresh directory {}.", normalizedPath, error);
                  threadMaster.executeOnEDT(() -> refreshingDirectories.remove(normalizedPath));
                }
              }));
    } catch (RuntimeException error) {
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
    Directory parent =
        node.getParent() instanceof DefaultMutableTreeNode parentNode
            ? (Directory) parentNode.getUserObject()
            : null;
    state.select(selected, parent);
    logState();
  }

  private void logState() {
    TreeFile selected = state.getSelected();
    LOGGER.info(
        "Tree state updated: selected={}, currentDirectory={}",
        selected == null ? "none" : selected.getPath(),
        state.getCurrentDirectory().getPath());
  }

  private void loadChildren(TreePath path) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (!(node.getUserObject() instanceof Directory directory)
        || directory.areChildrenLoaded()
        || !loadingDirectories.add(directory)) {
      return;
    }

    ThreadMaster threadMaster = ThreadMaster.getInstance();
    try {
      threadMaster.register(
          new RunnableNode(
              () -> {
                try {
                  directory.loadChildren();
                  threadMaster.executeOnEDT(
                      () -> {
                        node.removeAllChildren();
                        for (TreeFile child : directory.getChildren()) {
                          node.add(createNode(child));
                        }
                        ((DefaultTreeModel) getModel()).nodeStructureChanged(node);
                        loadingDirectories.remove(directory);
                      });
                } catch (IOException exception) {
                  LOGGER.error("Unable to load directory {}.", directory.getPath(), exception);
                  threadMaster.executeOnEDT(() -> loadingDirectories.remove(directory));
                }
              }));
    } catch (RuntimeException exception) {
      loadingDirectories.remove(directory);
      LOGGER.error("Unable to schedule loading directory {}.", directory.getPath(), exception);
    }
  }

  static DefaultMutableTreeNode createNode(TreeFile file) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(file);
    if (file instanceof Directory directory) {
      if (directory.areChildrenLoaded()) {
        for (TreeFile child : directory.getChildren()) {
          node.add(createNode(child));
        }
      } else {
        node.add(new DefaultMutableTreeNode());
      }
    }
    return node;
  }

  public void createNewFile() {
    fileActions.createNewFile();
  }

  public void createNewDirectory() {
    fileActions.createNewDirectory();
  }
}
