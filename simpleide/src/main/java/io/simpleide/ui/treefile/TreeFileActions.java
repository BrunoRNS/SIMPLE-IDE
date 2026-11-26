package io.simpleide.ui.treefile;

import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;

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
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.edt.listeners.MouseNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.utils.service.FileService;

/**
 * Context menu actions for the project tree.
 *
 * <p>
 * Adds new-file, new-folder, rename and delete entries to a popup menu, prompts for the
 * required input and reports failures through the application popups. All actions run
 * through EDT nodes and update the tree model after the file system operation.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class TreeFileActions {

	private static final Logger LOGGER = LoggerFactory.getLogger(TreeFileActions.class);

	private final Tree tree;

	private final TreeState state;

	private final FileService fileService = new FileService();

	private JPopupMenu contextMenu;

	private JMenuItem copyItem;

	private JMenuItem cutItem;

	private JMenuItem pasteItem;

	private JMenuItem renameItem;

	private JMenuItem deleteItem;

	TreeFileActions(Tree tree, TreeState state) {
		this.tree = tree;
		this.state = state;
	}

	void installContextMenu() {
		contextMenu = new JPopupMenu();
		addMenuItem(contextMenu, "New file", this::promptCreateFile);
		addMenuItem(contextMenu, "New folder", this::promptCreateDirectory);
		copyItem = addMenuItem(contextMenu, "Copy", this::copySelected);
		cutItem = addMenuItem(contextMenu, "Cut", this::cutSelected);
		pasteItem = addMenuItem(contextMenu, "Paste", this::pasteSelected);
		renameItem = addMenuItem(contextMenu, "Rename", this::promptRename);
		deleteItem = addMenuItem(contextMenu, "Delete", this::promptDelete);

		tree.addMouseListener(new MouseNode().onPress(this::showPopup).onRelease(this::showPopup));
	}

	private void showPopup(MouseEvent event) {
		if (!event.isPopupTrigger() || contextMenu == null) {
			return;
		}

		TreePath clickedPath = tree.getPathForLocation(event.getX(), event.getY());
		if (clickedPath != null) {
			tree.setSelectionPath(clickedPath);
		}
		updateMenuActions(renameItem, deleteItem, copyItem, cutItem, pasteItem);
		SwingUtilities.updateComponentTreeUI(contextMenu);
		contextMenu.show(event.getComponent(), event.getX(), event.getY());
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

	void copySelected() {
		TreePath selectedPath = tree.getSelectionPath();
		TreeFile selected = state.getSelected();
		if (!isSelectedPath(selectedPath, selected) || selectedPath.getParentPath() == null) {
			return;
		}
		TreeFileClipboard.getInstance().setCopied(List.of(selected));
	}

	void cutSelected() {
		TreePath selectedPath = tree.getSelectionPath();
		TreeFile selected = state.getSelected();
		if (!isSelectedPath(selectedPath, selected) || selectedPath.getParentPath() == null) {
			return;
		}
		TreeFileClipboard.getInstance().setCut(List.of(selected));
	}

	void pasteSelected() {
		Directory targetDirectory = state.getCurrentDirectory();
		if (targetDirectory == null) {
			return;
		}

		List<TreeFile> entries = TreeFileClipboard.getInstance().getEntries();
		boolean isCut = TreeFileClipboard.getInstance().isCut();
		if (entries.isEmpty()) {
			return;
		}
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.register(new RunnableNode(() -> pasteInBackground(targetDirectory, entries, isCut, threadMaster)));
	}

	private void pasteInBackground(Directory targetDirectory, List<TreeFile> entries, boolean isCut,
			ThreadMaster threadMaster) {
		try {
			for (TreeFile entry : entries) {
				Path sourcePath = entry.getPath();
				Path targetPath = targetDirectory.getPath().resolve(sourcePath.getFileName());
				if (Files.exists(targetPath)) {
					threadMaster.executeOnEDT(() -> new WarningPopUP("File exists", "A file or directory named \""
							+ targetPath.getFileName() + "\" already exists in the target directory.", tree)
						.startManaged());
					return;
				}
				if (isCut) {
					Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
				}
				else {
					copyRecursively(sourcePath, targetPath);
				}
			}
			targetDirectory.refreshChildren();
			threadMaster.executeOnEDT(() -> {
				refreshDirectoryNode(targetDirectory);
				if (isCut) {
					TreeFileClipboard.getInstance().clear();
				}
			});
		}
		catch (IllegalArgumentException exception) {
			threadMaster
				.executeOnEDT(() -> new WarningPopUP("Invalid name", exception.getMessage(), tree).startManaged());
		}
		catch (FileAlreadyExistsException exception) {
			threadMaster.executeOnEDT(
					() -> new WarningPopUP("File exists", "A file or directory with that name already exists.", tree)
						.startManaged());
		}
		catch (IOException exception) {
			LOGGER.error("Unable to paste.", exception);
			threadMaster.executeOnEDT(() -> showError(exception));
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

	private JMenuItem addMenuItem(JPopupMenu menu, String label, Runnable action) {
		JMenuItem item = new JMenuItem(label);
		item.addActionListener(new ActionNode(event -> action.run()));
		menu.add(item);
		return item;
	}

	private void updateMenuActions(JMenuItem rename, JMenuItem delete, JMenuItem copy, JMenuItem cut, JMenuItem paste) {
		TreePath selectedPath = tree.getSelectionPath();
		TreeFile selected = getSelectedFile(selectedPath);
		boolean canEdit = selected != null && selectedPath.getParentPath() != null;
		boolean canDelete = selected != null && selected.getPath().getParent() != null;
		boolean canPaste = !TreeFileClipboard.getInstance().isEmpty() && state.getCurrentDirectory() != null;
		rename.setEnabled(canEdit);
		delete.setEnabled(canDelete);
		copy.setEnabled(canEdit);
		cut.setEnabled(canEdit);
		paste.setEnabled(canPaste);
	}

	private void promptRename() {
		TreeFile selected = state.getSelected();
		TreePath selectedPath = tree.getSelectionPath();
		if (!isSelectedPath(selectedPath, selected) || selectedPath.getParentPath() == null) {
			return;
		}

		new InputPopUP("Rename", "New name for: " + selected.getName(), tree, this::renameSelected).startManaged();
	}

	void renameSelected(String name) {
		TreeFile selected = state.getSelected();
		TreePath selectedPath = tree.getSelectionPath();
		if (name == null || name.isBlank() || !isSelectedPath(selectedPath, selected)) {
			return;
		}

		Directory parentDirectory = getParentDirectory(selectedPath);
		if (parentDirectory == null) {
			return;
		}
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		Path selectedPathOnDisk = selected.getPath();
		threadMaster.register(new RunnableNode(() -> {
			try {
				Path renamedPath = fileService.renameFile(selectedPathOnDisk, name);
				List<TreeFile> refreshedChildren = parentDirectory.readChildrenFromDisk();
				threadMaster.executeOnEDT(() -> {
					parentDirectory.replaceChildren(refreshedChildren);
					refreshDirectoryNode(parentDirectory);
					selectPath(renamedPath);
				});
			}
			catch (IllegalArgumentException exception) {
				threadMaster
					.executeOnEDT(() -> new WarningPopUP("Invalid name", exception.getMessage(), tree).startManaged());
			}
			catch (IOException exception) {
				LOGGER.error("Unable to rename {}.", selectedPathOnDisk, exception);
				threadMaster.executeOnEDT(() -> showError(exception));
			}
		}));
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
		int confirmation = JOptionPane.showConfirmDialog(tree, message, "Confirm deletion", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (confirmation != JOptionPane.YES_OPTION) {
			return;
		}
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		Directory parentDirectory = getParentDirectory(selectedPath);
		threadMaster.register(
				new RunnableNode(() -> deleteInBackground(selectedPath, selected, parentDirectory, threadMaster)));
	}

	private void deleteInBackground(TreePath selectedPath, TreeFile selected, Directory parentDirectory,
			ThreadMaster threadMaster) {
		try {
			Path selectedFile = selected.getPath();
			fileService.deleteFile(selectedFile);
			if (selectedPath.getParentPath() == null) {
				Directory replacementRoot = new Directory(selectedFile.getParent());
				threadMaster.executeOnEDT(() -> tree.setRoot(replacementRoot));
				return;
			}
			if (parentDirectory == null) {
				return;
			}
			parentDirectory.refreshChildren();
			threadMaster.executeOnEDT(() -> {
				refreshDirectoryNode(parentDirectory);
				selectPath(parentDirectory.getPath());
			});
		}
		catch (IOException exception) {
			LOGGER.error("Unable to delete {}.", selected.getPath(), exception);
			threadMaster.executeOnEDT(() -> showError(exception));
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
		new InputPopUP("Create " + type, "Name of the new " + type + ":", tree, name -> create(name, directory, type))
			.startManaged();
	}

	void create(String name, boolean directory, String type) {
		if (name == null || name.isBlank()) {
			return;
		}

		Directory parent = state.getCurrentDirectory();
		if (parent == null) {
			return;
		}
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.register(new RunnableNode(() -> {
			try {
				List<TreeFile> loadedChildren = parent.areChildrenLoaded() ? null : parent.readChildrenFromDisk();
				TreeFile created = fileService.createNewFile(parent, name, directory);
				threadMaster.executeOnEDT(() -> {
					if (loadedChildren != null) {
						parent.replaceChildren(loadedChildren);
					}
					parent.addChild(created);
					refreshDirectoryNode(parent);
				});
			}
			catch (IllegalArgumentException exception) {
				threadMaster
					.executeOnEDT(() -> new WarningPopUP("Invalid name", exception.getMessage(), tree).startManaged());
			}
			catch (IOException exception) {
				LOGGER.error("Unable to create {} in {}.", type, parent.getPath(), exception);
				threadMaster.executeOnEDT(() -> showError(exception));
			}
		}));
	}

	void refreshDirectoryNode(Directory directory) {
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
		Object parentObject = ((DefaultMutableTreeNode) path.getParentPath().getLastPathComponent()).getUserObject();
		return parentObject instanceof Directory directory ? directory : null;
	}

	private void showError(IOException exception) {
		new ErrorPopUP(exception.getMessage(), tree, ErrorTypePopUp.INTERNAL_ERROR).startManaged();
	}

}
