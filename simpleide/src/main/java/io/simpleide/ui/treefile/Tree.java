package io.simpleide.ui.treefile;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.swing.JTree;
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

  public Tree(Directory root) {
    super(createNode(root));
    state = new TreeState(root);
    addTreeSelectionListener(event -> updateState(getSelectionPath()));
    LOGGER.info("Tree state initialized: currentDirectory={}", root.getPath());
    addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) {
        loadChildren(event.getPath());
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) {
      }
    });
  }

  public TreeState getState() {
    return state;
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
        ? (Directory) parentNode.getUserObject()
        : null;
    state.select(selected, parent);
    logState();
  }

  private void logState() {
    TreeFile selected = state.getSelected();
    LOGGER.info("Tree state updated: selected={}, currentDirectory={}",
        selected == null ? "none" : selected.getPath(),
        state.getCurrentDirectory().getPath());
  }

  private void loadChildren(TreePath path) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (!(node.getUserObject() instanceof Directory directory)
        || directory.areChildrenLoaded()) {
      return;
    }

    try {
      directory.loadChildren();
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to load directory " + directory.getPath(), e);
    }

    node.removeAllChildren();
    for (TreeFile child : directory.getChildren()) {
      node.add(createNode(child));
    }
    ((DefaultTreeModel) getModel()).nodeStructureChanged(node);
  }

  private static DefaultMutableTreeNode createNode(TreeFile file) {
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
}