package io.simpleide.frame;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.InfoPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.TreeSelectionNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorTab;
import io.simpleide.ui.sidebar.SidebarView;
import io.simpleide.ui.terminal.TerminalWorkingDirectory;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.SimpleIDEFile;
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.TreeFileFactory;
import io.simpleide.ui.treefile.Watcher;

/**
 * Opens, closes and refreshes the project workspace shown by the main window.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class MainFrameWorkspaceController {

	private static final Logger logger = LoggerFactory.getLogger(MainFrameWorkspaceController.class);

	private final MainFrame frame;

	MainFrameWorkspaceController(MainFrame frame) {
		this.frame = frame;
	}

	void openFolder() {
		Path start = frame.currentRoot != null ? frame.currentRoot : Path.of(System.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser(start.toFile());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Open Folder");
		if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		loadFolder(chooser.getSelectedFile().toPath());
	}

	void loadFolder(Path path) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					Directory root = new Directory(path);
					if (frame.editorManager != null) {
						threadMaster.executeOnEDT(() -> frame.editorManager.closeAllEditors());
					}
					threadMaster.executeOnEDT(() -> showFolder(root));
				}
				catch (IOException error) {
					logger.error("Unable to load project directory {}.", path, error);
					threadMaster.executeOnEDT(() -> new ErrorPopUP("Not found: " + error.getMessage(),
							frame.getRootPane(), ErrorTypePopUp.INTERNAL_ERROR)
						.startManaged());
				}
			}));
		}
		catch (RuntimeException error) {
			logger.error("Unable to schedule loading project directory {}.", path, error);
		}
	}

	void showFolder(Directory root) {
		if (frame.tree == null) {
			frame.tree = new Tree(root);
			frame.tree.setRootChangeListener(this::updateWatcherRoot);
			frame.tree.addTreeSelectionListener(new TreeSelectionNode(this::onTreeSelection));
		}
		else {
			frame.tree.setRoot(root);
		}
		frame.currentRoot = root.getPath().toAbsolutePath().normalize();
		frame.explorerPanel.showTree(frame.tree);
		frame.gitPanel.setOpenedFolder(frame.currentRoot);
		updateWatcherRoot(root);
		frame.showSidebarView(SidebarView.EXPLORER);
	}

	private void onTreeSelection(TreeSelectionEvent event) {
		TreePath selectedPath = event.getNewLeadSelectionPath();
		if (selectedPath == null) {
			return;
		}

		Object selectedNode = selectedPath.getLastPathComponent();
		if (selectedNode instanceof DefaultMutableTreeNode node
				&& node.getUserObject() instanceof SimpleIDEFile selectedFile) {
			frame.editorManager.openFile(selectedFile);
		}
	}

	void openGitFile(Path path) {
		try {
			frame.editorManager.openFile(new SimpleIDEFile(path));
		}
		catch (IOException error) {
			logger.error("Unable to open changed Git file {}.", path.getFileName(), error);
			new ErrorPopUP(error.getMessage(), frame.getRootPane(), ErrorTypePopUp.INTERNAL_ERROR).startManaged();
		}
	}

	private void updateWatcherRoot(Directory root) {
		if (frame.tree == null) {
			return;
		}
		try {
			if (frame.watcher == null) {
				frame.watcher = new Watcher(frame.tree);
				frame.watcher.start(root.getPath());
			}
			else {
				frame.watcher.setRoot(root.getPath());
			}
		}
		catch (IOException | IllegalArgumentException error) {
			logger.error("Unable to start file watcher for {}.", root.getPath(), error);
		}
	}

	void closeWatcher() {
		if (frame.watcher != null) {
			frame.watcher.close();
			frame.watcher = null;
		}
	}

	void closeFolder() {
		if (frame.tree == null && frame.currentRoot == null) {
			return;
		}

		if (frame.editorManager != null) {
			ThreadMaster.getInstance().executeOnEDT(() -> frame.editorManager.closeAllEditors());
		}
		closeWatcher();
		frame.tree = null;
		frame.currentRoot = null;
		frame.treePane.setViewportView(null);
		frame.explorerPanel.showNoFolder();
		frame.gitPanel.setOpenedFolder(null);
		frame.showSidebarView(SidebarView.EXPLORER);
	}

	Path terminalWorkingDirectory() {
		return TerminalWorkingDirectory.resolve(frame.currentRoot, System.getProperty("user.home"),
				System.getProperty("user.dir"));
	}

	void createNewFile() {
		if (frame.tree != null) {
			frame.tree.createNewFile();
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("New File");
		chooser.setSelectedFile(new File("untitled.txt"));
		if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		Path path = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		if (Files.exists(path) && JOptionPane.showConfirmDialog(frame, "Overwrite " + path.getFileName() + "?",
				"New File", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}
		createFileAndOpen(path);
	}

	private void createFileAndOpen(Path path) {
		Path parent = path.getParent();
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					if (parent != null) {
						Files.createDirectories(parent);
					}
					Files.writeString(path, "");
					SimpleIDEFile file = new SimpleIDEFile(path);
					threadMaster.executeOnEDT(() -> {
						if (parent != null && (frame.currentRoot == null || !path.startsWith(frame.currentRoot))) {
							loadFolder(parent);
						}
						frame.editorManager.openFile(file);
					});
				}
				catch (IOException error) {
					logger.error("Unable to create file {}.", path, error);
					threadMaster.executeOnEDT(
							() -> new ErrorPopUP("Unable to create " + path.getFileName() + ": " + error.getMessage(),
									frame.getRootPane(), ErrorTypePopUp.INTERNAL_ERROR)
								.startManaged());
				}
			}));
		}
		catch (RuntimeException error) {
			logger.error("Unable to schedule creating file {}.", path, error);
		}
	}

	void createNewDirectory() {
		if (frame.tree == null) {
			new InfoPopUP("Information", "Open a folder before creating a new folder.", frame.getRootPane())
				.startManaged();
			return;
		}
		frame.tree.createNewDirectory();
	}

	void openSearchMatch(Path path, int line) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					SimpleIDEFile file = new SimpleIDEFile(path);
					threadMaster.executeOnEDT(() -> {
						frame.editorManager.openFile(file);
						EditorTab tab = frame.editorManager.getOpenedEditor(path.toAbsolutePath().normalize());
						if (tab == null) {
							return;
						}
						Editor editor = tab.getEditor();
						if (editor == null) {
							return;
						}
						try {
							editor.setCaretPosition(editor.getLineStartOffset(Math.max(0, line - 1)));
							editor.requestFocusInWindow();
						}
						catch (BadLocationException error) {
							logger.debug("Unable to navigate to line {} in {}.", line, path, error);
						}
					});
				}
				catch (IOException error) {
					logger.error("Unable to open search result {}.", path, error);
				}
			}));
		}
		catch (RuntimeException error) {
			logger.error("Unable to schedule opening search result {}.", path, error);
		}
	}

	/**
	 * Loads the project tree directly into the given scroll pane.
	 *
	 * <p>
	 * Legacy v1.0.0beta implementation kept for source compatibility with code that
	 * reflected on or copied this private helper. New code must use {@link #openFolder()}
	 * or {@link #loadFolder(Path)}, which also update the explorer panel, the current
	 * root and the sidebar state.
	 * @param treePane scroll pane that receives the tree
	 * @deprecated since v1.1.0, use {@link #loadFolder(Path)} instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void loadTree(JScrollPane treePane) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.register(new RunnableNode(() -> {
			try {
				Directory root = TreeFileFactory.loadRootDirectory();
				threadMaster.executeOnEDT(() -> {
					frame.tree = new Tree(root);
					frame.tree.setRootChangeListener(this::updateWatcherRoot);
					updateWatcherRoot(root);
					frame.tree.addTreeSelectionListener(new TreeSelectionNode(event -> {
						TreePath selectedPath = event.getNewLeadSelectionPath();
						if (selectedPath == null) {
							return;
						}
						Object selectedNode = selectedPath.getLastPathComponent();
						if (selectedNode instanceof DefaultMutableTreeNode node
								&& node.getUserObject() instanceof SimpleIDEFile selectedFile) {
							frame.editorManager.openFile(selectedFile);
						}
					}));
					treePane.setViewportView(frame.tree);
					treePane.revalidate();
					treePane.repaint();
				});
			}
			catch (IOException exception) {
				logger.error("Unable to load the project tree.", exception);
			}
		}));
	}

	/**
	 * Opens a directory chooser and replaces the project root.
	 *
	 * <p>
	 * Legacy v1.0.0beta implementation kept for source compatibility. New code must use
	 * {@link #openFolder()}, which also updates the explorer panel, the current root and
	 * the sidebar state.
	 * @deprecated since v1.1.0, use {@link #openFolder()} instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void setDirectory() {
		if (frame.tree == null) {
			new InfoPopUP("Information", "The project tree is still loading.", frame.getRootPane()).startManaged();
			return;
		}

		JFileChooser chooser = new JFileChooser(frame.tree.getState().getCurrentDirectory().getPath().toFile());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		Path path = chooser.getSelectedFile().toPath();
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.register(new RunnableNode(() -> {
			try {
				Directory root = new Directory(path);
				threadMaster.executeOnEDT(() -> frame.tree.setRoot(root));
			}
			catch (IOException exception) {
				logger.error("Unable to load project directory {}.", path, exception);
				threadMaster.executeOnEDT(
						() -> new ErrorPopUP(exception.getMessage(), frame.getRootPane(), ErrorTypePopUp.INTERNAL_ERROR)
							.startManaged());
			}
		}));
	}

}
