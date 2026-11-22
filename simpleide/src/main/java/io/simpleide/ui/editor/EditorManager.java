package io.simpleide.ui.editor;

import java.awt.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.lsp.core.DocumentManager;
import io.simpleide.lsp.core.LanguageServerManager;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.icon.MaterialIcons;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.FileContentKind;
import io.simpleide.ui.treefile.TypeFile;

/**
 * Owns the editor tab set and the language support attached to it.
 *
 * <p>
 * The manager creates the tabbed pane, shows the welcome page, opens files as tabs,
 * connects each document to a language server, tracks modifications, saves, closes and
 * unregisters editors from the theme manager. The first instance created becomes the
 * application-wide {@link #getInstance() singleton} used by tab close buttons and
 * navigation actions.
 *
 * <p>
 * Covered by {@code EditorManagerTest}.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/editor.md">docs/api/editor.md</a>
 */
public class EditorManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(EditorManager.class);

	private final Map<Path, EditorTab> openedEditors = new HashMap<>();

	private static EditorManager instance = null;

	private final DocumentManager manager;

	private final LanguageServerManager languageServers;

	private final EditorActions editorActions;

	private JTabbedPane jTabbedPane;

	private Runnable createFile;

	@SuppressWarnings("unused")
	private Runnable createFolder;

	private Runnable openFolder;

	/**
	 * Creates the editor manager without a language server status listener.
	 * @param createFile callback that starts new-file creation
	 * @param createFolder callback that starts folder creation
	 * @param openFolder callback that starts folder selection
	 */
	public EditorManager(Runnable createFile, Runnable createFolder, Runnable openFolder) {
		this(createFile, createFolder, openFolder, ignored -> {
		});
	}

	/**
	 * Creates the editor manager and its language server manager.
	 * @param createFile callback that starts new-file creation
	 * @param createFolder callback that starts folder creation
	 * @param openFolder callback that starts folder selection
	 * @param statusListener listener for language server lifecycle events
	 */
	public EditorManager(Runnable createFile, Runnable createFolder, Runnable openFolder,
			Consumer<LspStatus> statusListener) {
		if (instance == null) {
			instance = this;
			this.createFile = createFile;
			this.createFolder = createFolder;
			this.openFolder = openFolder;
			this.jTabbedPane = new JTabbedPane();
			configJTabbedPane();
			showWelcomeScreen();
		}
		this.manager = new DocumentManager();
		this.languageServers = new LanguageServerManager(statusListener);
		this.editorActions = new EditorActions(this);
	}

	/**
	 * Returns the application-wide manager instance.
	 * @return the first created manager, or {@code null} before initialization
	 */
	public static EditorManager getInstance() {
		return instance;
	}

	/**
	 * Returns the tabbed pane that holds the editor tabs.
	 * @return the shared tabbed pane
	 */
	public JTabbedPane getJTabbedPane() {
		return jTabbedPane;
	}

	/**
	 * Returns the editor of the selected tab.
	 * @return the selected text editor, or {@code null} when an image, binary file, or
	 * welcome page is selected
	 */
	public Editor getSelectedEditor() {
		if (jTabbedPane.getSelectedComponent() instanceof EditorTab tab) {
			return tab.getEditor();
		}
		return null;
	}

	@SuppressWarnings("")
	/**
	 * Opens a file in a new tab, or selects its existing tab.
	 *
	 * <p>
	 * The file is normalized before lookup. Opening a file also enables local language
	 * support and connects a language server only for readable text files. Images and
	 * binary files use their dedicated viewers and do not start a language server.
	 * @param file file to open, ignored when {@code null}
	 */
	public void openFile(File file) {
		if (file == null) {
			return;
		}

		Path path = file.getPath().toAbsolutePath().normalize();

		EditorTab existing = openedEditors.get(path);

		if (existing != null) {
			jTabbedPane.setSelectedComponent(existing);
			return;
		}

		Document document = manager.open(path, getLanguageId(file.getTypeFile()));

		EditorTab editorTab;
		try {
			editorTab = new EditorTab(file, document);
		}
		catch (UncheckedIOException error) {
			manager.close(document.getUri());
			LOGGER.error("Unable to open file {} in its content viewer.", path, error.getCause());
			JOptionPane.showMessageDialog(jTabbedPane,
					"Unable to open " + file.getName() + ": " + error.getCause().getMessage(), "Unable to open file",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		removeWelcomeScreen();

		jTabbedPane.addTab(file.getName(), editorTab.getFileIcon(), editorTab);
		jTabbedPane.setTabComponentAt(jTabbedPane.indexOfComponent(editorTab), createTabHeader(editorTab));

		openedEditors.put(path, editorTab);
		jTabbedPane.setSelectedComponent(editorTab);

		if (file.isContentLoaded()) {
			configureLoadedTab(editorTab, path, true);
		}
		else {
			loadFileInBackground(editorTab, path, true);
		}
	}

	/**
	 * Adds an already constructed tab to the tabbed pane.
	 * @param tab tab to add, ignored when {@code null}
	 */
	public void addNewTab(EditorTab tab) {
		if (tab == null) {
			return;
		}

		if (jTabbedPane.getTabCount() == 1 && "Welcome".equals(jTabbedPane.getTitleAt(0))) {
			jTabbedPane.removeAll();
		}
		jTabbedPane.addTab(tab.getName(), tab.getFileIcon(), tab);
		jTabbedPane.setTabComponentAt(jTabbedPane.indexOfComponent(tab), createTabHeader(tab));
		jTabbedPane.setSelectedComponent(tab);
		Path path = tab.getFile().getPath().toAbsolutePath().normalize();
		if (tab.getFile().isContentLoaded()) {
			configureLoadedTab(tab, path, false);
		}
		else {
			loadFileInBackground(tab, path, false);
		}
	}

	/**
	 * Closes every tab, disconnects their documents and shuts the language server manager
	 * down.
	 */
	public void closeAll() {
		try (languageServers) {
			openedEditors.values().forEach(tab -> {
				tab.close();
				manager.close(tab.getDocument().getUri());
				languageServers.disconnect(tab.getDocument());
				if (tab.getEditor() != null) {
					ThemeManager.getInstance().unregister(tab.getEditor());
				}
				if (tab.getHexEditor() != null) {
					ThemeManager.getInstance().unregister(tab.getHexEditor());
				}
			});
			openedEditors.clear();
		}
	}

	/**
	 * Closes a tab, prompting to save unsaved changes first.
	 * @param tab tab to close, ignored when {@code null} or not open
	 */
	public void closeTab(EditorTab tab) {
		if (tab == null || jTabbedPane.indexOfComponent(tab) < 0) {
			return;
		}

		if (tab.isEdit()) {
			int choice = JOptionPane.showConfirmDialog(tab,
					"Save changes to " + tab.getFile().getName() + " before closing?", "Unsaved changes",
					JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
				return;
			}
			if (choice == JOptionPane.YES_OPTION) {
				saveTab(tab);
				if (tab.isEdit()) {
					return;
				}
			}
		}

		int tabIndex = jTabbedPane.indexOfComponent(tab);
		Path path = tab.getFile().getPath().toAbsolutePath().normalize();
		tab.close();
		manager.close(tab.getDocument().getUri());
		languageServers.disconnect(tab.getDocument());
		if (tab.getEditor() != null) {
			ThemeManager.getInstance().unregister(tab.getEditor());
		}
		if (tab.getHexEditor() != null) {
			ThemeManager.getInstance().unregister(tab.getHexEditor());
		}
		openedEditors.remove(path, tab);
		jTabbedPane.removeTabAt(tabIndex);
		if (jTabbedPane.getTabCount() == 0) {
			showWelcomeScreen();
		}
	}

	/**
	 * Saves the selected tab when it has unsaved changes.
	 */
	public void saveSelectedFile() {
		EditorTab selectedTab = getSelectedTab();
		if (selectedTab != null) {
			saveTab(selectedTab);
		}
	}

	/**
	 * Saves every modified editor before application shutdown.
	 * @return {@code true} when every modified file was saved successfully
	 */
	public boolean saveAllModifiedFiles() {
		boolean saved = true;
		for (EditorTab tab : openedEditors.values()) {
			boolean changed = tab.isEdit() || (tab.getEditor() != null
					&& !java.util.Objects.equals(tab.getFile().getCode().toString(), tab.getEditor().getText()));
			if (!changed) {
				continue;
			}
			try {
				tab.save();
			}
			catch (IOException error) {
				saved = false;
				LOGGER.error("Unable to save modified file during application shutdown: {}", tab.getFile().getPath(),
						error);
			}
		}
		return saved;
	}

	/**
	 * Returns the tab that has a path open.
	 * @param path normalized source path
	 * @return the matching tab, or {@code null}
	 */
	public EditorTab getOpenedEditor(Path path) {
		return openedEditors.get(path);
	}

	/**
	 * Selects a tab in the tabbed pane.
	 * @param tab tab to select
	 */
	public void selectEditorTab(EditorTab tab) {
		jTabbedPane.setSelectedComponent(tab);
	}

	/** Refreshes file icons after a theme or global font-size change. */
	public static void refreshFileIcons() {
		EditorManager manager = instance;
		if (manager == null || manager.jTabbedPane == null) {
			return;
		}
		java.awt.Color tabBackground = UIManager.getColor("TabbedPane.background");
		java.awt.Color tabForeground = UIManager.getColor("TabbedPane.foreground");
		if (tabBackground != null) {
			manager.jTabbedPane.setBackground(tabBackground);
		}
		if (tabForeground != null) {
			manager.jTabbedPane.setForeground(tabForeground);
		}
		int selectedIndex = manager.jTabbedPane.getSelectedIndex();
		for (int tabIndex = 0; tabIndex < manager.jTabbedPane.getTabCount(); tabIndex++) {
			Component page = manager.jTabbedPane.getComponentAt(tabIndex);
			if (!(page instanceof EditorTab tab)) {
				if (page instanceof JComponent component && tabBackground != null) {
					component.setBackground(tabBackground);
					component.setOpaque(true);
				}
				continue;
			}
			Icon icon = MaterialIcons.forFile(tab.getFile().getPath());
			if (tab.getEditor() != null) {
				tab.getEditor().refreshUiFont();
			}
			manager.jTabbedPane.setIconAt(tabIndex, icon);
			tab.getButtonExit().refreshIconSize();
			Component header = manager.jTabbedPane.getTabComponentAt(tabIndex);
			if (header instanceof JPanel panel && panel.getComponentCount() > 0
					&& panel.getComponent(0) instanceof JLabel label) {
				label.setIcon(icon);
				label.setForeground(tabForeground(tabIndex == selectedIndex));
				tab.getButtonExit().setForeground(tabForeground(tabIndex == selectedIndex));
				panel.revalidate();
				panel.repaint();
			}
		}
	}

	private static java.awt.Color tabForeground(boolean selected) {
		java.awt.Color foreground = UIManager
			.getColor(selected ? "TabbedPane.selectedForeground" : "TabbedPane.foreground");
		return foreground != null ? foreground : UIManager.getColor("Label.foreground");
	}

	private JPanel createTabHeader(EditorTab tab) {
		JPanel header = new JPanel();
		header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.LINE_AXIS));
		header.setOpaque(false);
		JLabel fileLabel = new JLabel(tab.getName(), tab.getFileIcon(), SwingConstants.LEADING);
		fileLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
		fileLabel.setForeground(tabForeground(true));
		tab.getButtonExit().setAlignmentY(Component.CENTER_ALIGNMENT);
		tab.getButtonExit().setForeground(tabForeground(true));
		header.add(fileLabel);
		header.add(tab.getButtonExit());
		return header;
	}

	private void loadFileInBackground(EditorTab tab, Path path, boolean connectLanguageServer) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					tab.getFile().loadContent();
					threadMaster.executeOnEDT(() -> {
						if (tab.isClosed() || !isTabAttached(tab, path)) {
							return;
						}
						try {
							tab.showLoadedContent();
							configureLoadedTab(tab, path, connectLanguageServer);
						}
						catch (RuntimeException | LinkageError error) {
							LOGGER.error("Unable to initialize the content viewer for {}.", path, error);
							tab.showLoadError(error);
						}
					});
				}
				catch (IOException error) {
					LOGGER.error("Unable to load file {}.", path, error);
					threadMaster.executeOnEDT(() -> {
						if (!tab.isClosed() && isTabAttached(tab, path)) {
							tab.showLoadError(error);
						}
					});
				}
			}));
		}
		catch (RuntimeException error) {
			LOGGER.error("Unable to schedule loading file {}.", path, error);
			tab.showLoadError(error);
		}
	}

	private void configureLoadedTab(EditorTab tab, Path path, boolean connectLanguageServer) {
		editorActions.configure(tab);
		try {
			tab.enableLocalLanguageSupport();
		}
		catch (RuntimeException | LinkageError error) {
			LOGGER.error("Unable to initialize local language support for {}; the file remains open.", path, error);
		}
		try {
			if (tab.getEditor() != null) {
				ThemeManager.getInstance().register(tab.getEditor());
			}
			if (tab.getHexEditor() != null) {
				ThemeManager.getInstance().register(tab.getHexEditor());
			}
		}
		catch (RuntimeException error) {
			LOGGER.error("Unable to apply the current theme to {}; the file remains open.", path, error);
		}
		if (connectLanguageServer && tab.getEditor() != null
				&& tab.getFile().getContentKind() == FileContentKind.TEXT) {
			languageServers.connect(tab.getDocument(), tab::publishDiagnostics)
				.whenComplete((client, error) -> completeLanguageServerConnection(tab, client, error));
		}
	}

	private boolean isTabAttached(EditorTab tab, Path path) {
		return openedEditors.get(path) == tab || jTabbedPane.indexOfComponent(tab) >= 0;
	}

	void saveTab(EditorTab tab) {
		if (!tab.isEdit()) {
			return;
		}
		try {
			tab.save();
		}
		catch (IOException error) {
			LOGGER.warn("Save failed; keeping unsaved changes for {}.", tab.getFile().getPath());
			if (tab.getHexEditor() != null) {
				JOptionPane.showMessageDialog(tab,
						"Unable to save " + tab.getFile().getName() + ": " + error.getMessage(), "Save failed",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private EditorTab getSelectedTab() {
		return jTabbedPane.getSelectedComponent() instanceof EditorTab tab ? tab : null;
	}

	private void showWelcomeScreen() {
		jTabbedPane.addTab("Welcome", EditorWelcomeScreen.create(openFolder, createFile));
	}

	private String getLanguageId(TypeFile type) {
		return switch (type) {
			case JAVA -> "java";
			case CSHARP -> "csharp";
			case KOTLIN -> "kotlin";
			case GROOVY -> "groovy";
			case JAVASCRIPT -> "javascript";
			case GO -> "go";
			case RUST -> "rust";
			case PYTHON -> "python";
			case C -> "c";
			case CPP -> "cpp";
			default -> "plaintext";
		};
	}

	static void completeLanguageServerConnection(EditorTab tab, LspClient client, Throwable error) {
		if (error != null) {
			Throwable cause = error;

			if (error instanceof CompletionException && error.getCause() != null) {
				cause = error.getCause();
			}
			if (cause instanceof CancellationException) {
				return;
			}

			LOGGER.error("LSP connection failed for {}.", tab.getFile().getPath(), cause);

			if (client != null) {
				try {
					client.close();
				}
				catch (Exception closeError) {
					LOGGER.warn("Failed to close LSP client.", closeError);
				}
			}
		}

		ThreadMaster.getInstance().executeOnEDT(() -> {
			if (client == null || error != null) {
				tab.enableLocalLanguageSupport();
			}
			else {
				tab.connectLanguageServer(client);
			}
		});
	}

	private void configJTabbedPane() {
		this.jTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		this.jTabbedPane.addChangeListener(event -> refreshTabHeaderColors());
	}

	private void refreshTabHeaderColors() {
		int selectedIndex = jTabbedPane.getSelectedIndex();
		for (int tabIndex = 0; tabIndex < jTabbedPane.getTabCount(); tabIndex++) {
			Component header = jTabbedPane.getTabComponentAt(tabIndex);
			if (!(header instanceof JPanel panel)) {
				continue;
			}
			java.awt.Color foreground = tabForeground(tabIndex == selectedIndex);
			for (Component child : panel.getComponents()) {
				if (foreground != null) {
					child.setForeground(foreground);
				}
			}
		}
	}

	private void removeWelcomeScreen() {
		if (jTabbedPane.getTabCount() == 1 && "Welcome".equals(jTabbedPane.getTitleAt(0))) {
			jTabbedPane.removeAll();
		}
	}

}
