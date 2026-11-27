package io.simpleide.ui.editor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.lsp.core.DocumentManager;
import io.simpleide.lsp.core.LanguageServerManager;
import io.simpleide.thread.nodes.edt.listeners.ChangeNode;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.treefile.SimpleIDEFile;

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
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/editor.md">docs/api/editor.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class EditorManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(EditorManager.class);

	final Map<Path, EditorTab> openedEditors = new HashMap<>();

	final List<Consumer<EditorTab>> selectedTabListeners = new ArrayList<>();

	final List<Consumer<EditorTab>> diagnosticsListeners = new ArrayList<>();

	private static EditorManager instance = null;

	private final DocumentManager manager;

	final LanguageServerManager languageServers;

	final EditorActions editorActions;

	JTabbedPane jTabbedPane;

	private final EditorTabLoader tabLoader;

	private final EditorTabHeaders tabHeaders;

	private Runnable createFile;

	@SuppressWarnings("unused")
	private Runnable createFolder;

	private Runnable openFolder;

	private int editorTabSize = 4;

	private boolean editorWordWrap;

	private boolean editorLineNumbers = true;

	private boolean editorShowWhitespace;

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
		this.tabLoader = new EditorTabLoader(this);
		this.tabHeaders = new EditorTabHeaders(this);
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

	/**
	 * Returns the selected editor tab.
	 * @return the selected tab, or {@code null} when the welcome page is selected
	 */
	public EditorTab getSelectedEditorTab() {
		return jTabbedPane.getSelectedComponent() instanceof EditorTab tab ? tab : null;
	}

	/**
	 * Registers a listener notified when the selected editor tab changes.
	 * @param listener listener invoked on the EDT, must not be {@code null}
	 */
	public void addSelectedTabListener(Consumer<EditorTab> listener) {
		selectedTabListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
	}

	/**
	 * Registers a listener notified when diagnostics change for an editor tab.
	 * @param listener listener invoked with the updated tab, must not be {@code null}
	 */
	public void addDiagnosticsListener(Consumer<EditorTab> listener) {
		diagnosticsListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
	}

	/**
	 * Applies the configurable editor behavior options to every open tab and to tabs
	 * opened later.
	 * @param tabSize number of spaces used for a tab
	 * @param wordWrap whether long lines wrap
	 * @param lineNumbers whether the line number gutter is shown
	 * @param showWhitespace whether whitespace characters are painted
	 */
	public void applyEditorOptions(int tabSize, boolean wordWrap, boolean lineNumbers, boolean showWhitespace) {
		this.editorTabSize = tabSize;
		this.editorWordWrap = wordWrap;
		this.editorLineNumbers = lineNumbers;
		this.editorShowWhitespace = showWhitespace;
		for (EditorTab tab : openedEditors.values()) {
			tab.applyEditorOptions(tabSize, wordWrap, lineNumbers, showWhitespace);
		}
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
	public void openFile(SimpleIDEFile file) {
		if (file == null) {
			return;
		}

		Path path = file.getPath().toAbsolutePath().normalize();

		EditorTab existing = openedEditors.get(path);

		if (existing != null) {
			jTabbedPane.setSelectedComponent(existing);
			return;
		}

		Document document = manager.open(path, EditorTabLoader.getLanguageId(file.getTypeFile()));

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
		editorTab.applyEditorOptions(editorTabSize, editorWordWrap, editorLineNumbers, editorShowWhitespace);
		editorTab.setDiagnosticsListener(this::notifyDiagnostics);
		editorActions.configure(editorTab);
		removeWelcomeScreen();

		jTabbedPane.addTab(file.getName(), editorTab.getFileIcon(), editorTab);
		jTabbedPane.setTabComponentAt(jTabbedPane.indexOfComponent(editorTab), tabHeaders.createTabHeader(editorTab));

		openedEditors.put(path, editorTab);
		jTabbedPane.setSelectedComponent(editorTab);

		if (file.isContentLoaded()) {
			tabLoader.configureLoadedTab(editorTab, path, true);
		}
		else {
			tabLoader.loadFileInBackground(editorTab, path, true);
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

		tab.enableLocalLanguageSupport();
		editorActions.configure(tab);
		tab.applyEditorOptions(editorTabSize, editorWordWrap, editorLineNumbers, editorShowWhitespace);
		tab.setDiagnosticsListener(this::notifyDiagnostics);
		if (jTabbedPane.getTabCount() == 1 && "Welcome".equals(jTabbedPane.getTitleAt(0))) {
			jTabbedPane.removeAll();
		}
		jTabbedPane.addTab(tab.getName(), tab.getFileIcon(), tab);
		jTabbedPane.setTabComponentAt(jTabbedPane.indexOfComponent(tab), tabHeaders.createTabHeader(tab));
		jTabbedPane.setSelectedComponent(tab);
		Path path = tab.getFile().getPath().toAbsolutePath().normalize();
		if (tab.getFile().isContentLoaded()) {
			tabLoader.configureLoadedTab(tab, path, false);
		}
		else {
			tabLoader.loadFileInBackground(tab, path, false);
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
	 * Closes the selected editor tab, prompting to save unsaved changes first.
	 */
	public void closeSelectedTab() {
		closeTab(getSelectedEditorTab());
	}

	/**
	 * Closes every open editor tab and shows the welcome page. Unsaved files are handled
	 * by the same prompt used by a single tab close.
	 */
	public void closeAllEditors() {
		for (EditorTab tab : new ArrayList<>(openedEditors.values())) {
			closeTab(tab);
		}
		if (jTabbedPane.getTabCount() == 0) {
			showWelcomeScreen();
		}
	}

	/**
	 * Saves every modified editor.
	 * @return {@code true} when every modified file was saved successfully
	 */
	public boolean saveAllEditors() {
		return saveAllModifiedFiles();
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
	 * Returns a snapshot of paths currently open in editor tabs.
	 * @return normalized open file paths
	 */
	public Set<Path> getOpenFilePaths() {
		return Set.copyOf(openedEditors.keySet());
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
		EditorTabHeaders.refreshFileIcons(instance);
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

	/**
	 * Connects a document to its language server and returns the initialized client.
	 *
	 * <p>
	 * Used by the debug integration to reach the JDT language server that hosts the Java
	 * debug adapter. The returned future reuses the shared client when one already exists
	 * for the document's workspace.
	 * @param document document to connect
	 * @return a future completed with the language server client
	 */
	public java.util.concurrent.CompletableFuture<LspClient> connectLanguageServer(Document document) {
		return languageServers.connect(document, ignored -> {
		});
	}

	static void completeLanguageServerConnection(EditorTab tab, LspClient client, Throwable error) {
		EditorTabLoader.completeLanguageServerConnection(tab, client, error);
	}

	private void configJTabbedPane() {
		this.jTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		this.jTabbedPane.addChangeListener(new ChangeNode(event -> {
			tabHeaders.refreshTabHeaderColors();
			notifySelectedTab();
		}));
	}

	private void notifySelectedTab() {
		tabHeaders.notifySelectedTab();
	}

	private void notifyDiagnostics(EditorTab tab) {
		tabHeaders.notifyDiagnostics(tab);
	}

	private void removeWelcomeScreen() {
		if (jTabbedPane.getTabCount() == 1 && "Welcome".equals(jTabbedPane.getTitleAt(0))) {
			jTabbedPane.removeAll();
		}
	}

}
