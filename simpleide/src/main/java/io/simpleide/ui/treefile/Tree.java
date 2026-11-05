package io.simpleide.ui.treefile;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

public class Tree extends JTree {
  private static final Logger LOGGER = LoggerFactory.getLogger(Tree.class);

  private final TreeState state;
  private final Set<Directory> loadingDirectories =
      Collections.newSetFromMap(new IdentityHashMap<>());

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
        || directory.areChildrenLoaded()
        || !loadingDirectories.add(directory)) {
      return;
    }

    ThreadMaster threadMaster = ThreadMaster.getInstance();
    try {
      threadMaster.register(new RunnableNode(() -> {
        try {
          directory.loadChildren();
          threadMaster.executeOnEDT(() -> {
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
      throw exception;
    }
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