package io.simpleide.ui.treefile;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Enumeration;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.InputPopUP;
import io.simpleide.frame.popup.WarningPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.utils.service.FileService;

final class TreeFileActions {
  private static final Logger LOGGER = LoggerFactory.getLogger(TreeFileActions.class);

  private final Tree tree;
  private final TreeState state;
  private final FileService fileService = new FileService();

  TreeFileActions(Tree tree, TreeState state) {
    this.tree = tree;
    this.state = state;
  }

  @SuppressWarnings("unused")
  void installContextMenu() {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem newFile = addMenuItem(menu, "New file", this::promptCreateFile);
    JMenuItem newFolder = addMenuItem(menu, "New folder", this::promptCreateDirectory);
    JMenuItem rename = addMenuItem(menu, "Rename", this::promptRename);
    JMenuItem delete = addMenuItem(menu, "Delete", this::promptDelete);

    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        showPopup(event);
      }

      @Override
      public void mouseReleased(MouseEvent event) {
        showPopup(event);
      }

      private void showPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) {
          return;
        }

        TreePath clickedPath = tree.getPathForLocation(event.getX(), event.getY());
        if (clickedPath != null) {
          tree.setSelectionPath(clickedPath);
        }
        updateMenuActions(rename, delete);
        SwingUtilities.updateComponentTreeUI(menu);
        menu.show(event.getComponent(), event.getX(), event.getY());
      }
    });
  }

  void createNewFile() {
    promptCreate(false);
  }

  void createNewDirectory() {
    promptCreate(true);
  }

  void deleteSelected(TreePath selectedPath, TreeFile selected) throws IOException {
    if (!isSelectedPath(selectedPath, selected) || selected.getPath().getParent() == null) {
      return;
    }

    if (selectedPath.getParentPath() == null) {
      Directory replacementRoot = new Directory(selected.getPath().getParent());
      fileService.deleteFile(selected.getPath());
      replacementRoot.getChildren().removeIf(child -> child.getPath().equals(selected.getPath()));
      tree.setRoot(replacementRoot);
      return;
    }

    Directory parentDirectory = getParentDirectory(selectedPath);
    if (parentDirectory == null) {
      return;
    }

    fileService.deleteFile(selected.getPath());
    parentDirectory.refreshChildren();
    refreshDirectoryNode(parentDirectory);
    selectPath(parentDirectory.getPath());
  }

  private JMenuItem addMenuItem(JPopupMenu menu, String label, Runnable action) {
    JMenuItem item = new JMenuItem(label);
    item.addActionListener(new ActionNode(event -> action.run()));
    menu.add(item);
    return item;
  }

  private void updateMenuActions(JMenuItem rename, JMenuItem delete) {
    TreePath selectedPath = tree.getSelectionPath();
    TreeFile selected = getSelectedFile(selectedPath);
    rename.setEnabled(selected != null && selectedPath.getParentPath() != null);
    delete.setEnabled(selected != null && selected.getPath().getParent() != null);
  }

  private void promptRename() {
    TreeFile selected = state.getSelected();
    TreePath selectedPath = tree.getSelectionPath();
    if (!isSelectedPath(selectedPath, selected) || selectedPath.getParentPath() == null) {
      return;
    }

    new InputPopUP("Rename", "New name for: " + selected.getName(), tree,
        this::renameSelected).startManaged();
  }

  private void renameSelected(String name) {
    TreeFile selected = state.getSelected();
    TreePath selectedPath = tree.getSelectionPath();
    if (name == null || name.isBlank() || !isSelectedPath(selectedPath, selected)) {
      return;
    }

    Directory parentDirectory = getParentDirectory(selectedPath);
    if (parentDirectory == null) {
      return;
    }

    try {
      Path renamedPath = fileService.renameFile(selected.getPath(), name);
      parentDirectory.refreshChildren();
      refreshDirectoryNode(parentDirectory);
      selectPath(renamedPath);
    } catch (IllegalArgumentException exception) {
      new WarningPopUP("Invalid name", exception.getMessage(), tree).startManaged();
    } catch (IOException exception) {
      LOGGER.error("Unable to rename {}.", selected.getPath(), exception);
      showError(exception);
    }
  }

  private void promptDelete() {
    TreePath selectedPath = tree.getSelectionPath();
    TreeFile selected = state.getSelected();
    if (!isSelectedPath(selectedPath, selected) || selected.getPath().getParent() == null) {
      return;
    }

    String message = "Delete \"" + selected.getName() + "\"? Folders and their contents will also be deleted.";
    if (selectedPath.getParentPath() == null) {
      message += " The tree root will move to its parent directory.";
    }
    int confirmation = JOptionPane.showConfirmDialog(
        tree, message, "Confirm deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
    if (confirmation != JOptionPane.YES_OPTION) {
      return;
    }

    try {
      deleteSelected(selectedPath, selected);
    } catch (IOException exception) {
      LOGGER.error("Unable to delete {}.", selected.getPath(), exception);
      showError(exception);
    }
  }

  private void promptCreateFile() {
    promptCreate(false);
  }

  private void promptCreateDirectory() {
    promptCreate(true);
  }

  private void promptCreate(boolean directory) {
    String type = directory ? "directory" : "file";
    new InputPopUP("Create " + type, "Name of the new " + type + ":", tree,
        name -> create(name, directory, type)).startManaged();
  }

  private void create(String name, boolean directory, String type) {
    if (name == null || name.isBlank()) {
      return;
    }

    Directory parent = state.getCurrentDirectory();
    try {
      parent.loadChildren();
      TreeFile created = fileService.createNewFile(parent, name, directory);
      parent.addChild(created);
      refreshDirectoryNode(parent);
    } catch (IllegalArgumentException exception) {
      new WarningPopUP("Invalid name", exception.getMessage(), tree).startManaged();
    } catch (IOException exception) {
      LOGGER.error("Unable to create {} in {}.", type, parent.getPath(), exception);
      showError(exception);
    }
  }

  private void refreshDirectoryNode(Directory directory) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    Enumeration<?> nodes = root.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      if (node.getUserObject() == directory) {
        node.removeAllChildren();
        for (TreeFile child : directory.getChildren()) {
          node.add(Tree.createNode(child));
        }
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
        return;
      }
    }
  }

  private void selectPath(Path path) {
    TreePath treePath = findTreePath(path);
    if (treePath != null) {
      tree.setSelectionPath(treePath);
    }
  }

  private TreePath findTreePath(Path path) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    Enumeration<?> nodes = root.depthFirstEnumeration();
    while (nodes.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
      if (node.getUserObject() instanceof TreeFile file && file.getPath().equals(path)) {
        return new TreePath(node.getPath());
      }
    }
    return null;
  }

  private TreeFile getSelectedFile(TreePath path) {
    if (path == null) {
      return null;
    }
    Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    return userObject instanceof TreeFile file ? file : null;
  }

  private boolean isSelectedPath(TreePath path, TreeFile selected) {
    TreeFile pathFile = getSelectedFile(path);
    return selected != null && pathFile != null && pathFile.getPath().equals(selected.getPath());
  }

  private Directory getParentDirectory(TreePath path) {
    if (path == null || path.getParentPath() == null) {
      return null;
    }
    Object parentObject =
        ((DefaultMutableTreeNode) path.getParentPath().getLastPathComponent()).getUserObject();
    return parentObject instanceof Directory directory ? directory : null;
  }

  private void showError(IOException exception) {
    new ErrorPopUP(exception.getMessage(), tree, ErrorTypePopUp.INTERNAL_ERROR).startManaged();
  }
}
