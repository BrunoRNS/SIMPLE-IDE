package io.simpleide.frame;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.layout.MainLayout;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;
import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.InfoPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.frame.utils.ApplicationIcon;
import io.simpleide.frame.utils.LinuxStatusNotifierTray;
import io.simpleide.settings.IdeAction;
import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.KeybindingManager;
import io.simpleide.settings.SettingsService;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.edt.listeners.TreeSelectionNode;
import io.simpleide.thread.nodes.edt.listeners.WindowNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorManager;
import io.simpleide.ui.editor.EditorTab;
import io.simpleide.ui.editor.LspStatusBar;
import io.simpleide.ui.menubar.MenuBarFactory;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.menubar.menus.IdeMenuBar;
import io.simpleide.ui.sidebar.ActivityBar;
import io.simpleide.ui.sidebar.DebugConsolePanel;
import io.simpleide.ui.sidebar.ExplorerPanel;
import io.simpleide.ui.sidebar.SettingsPanel;
import io.simpleide.ui.sidebar.SidebarPanel;
import io.simpleide.ui.sidebar.SidebarView;
import io.simpleide.ui.sidebar.WorkspaceSearchPanel;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.Watcher;

/**
 * Main desktop window of Simple IDE.
 *
 * <p>
 * The frame wires the activity bar and its Explorer, Search and Settings views, the
 * editor manager, the Output, Debug Console and Terminal tabs, the status bar and the
 * menu bar together. It implements {@link Runnable} so it can be created on the Swing
 * Event Dispatch Thread by an {@code EDTNode}; every listener is routed through the
 * thread manager and every shortcut and settings change is persisted under
 * {@code ~/.simpleide/settings.json}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 */
public class MainFrame extends JFrame implements Runnable {

	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrame.class);

	private static final int SIDEBAR_WIDTH = 300;

	private Tree tree;

	private Watcher watcher;

	private Path currentRoot;

	private EditorManager editorManager;

	private BusyBoxTerminalController terminalController;

	private JScrollPane treePane;

	private SidebarPanel sidebarPanel;

	private ActivityBar activityBar;

	private ExplorerPanel explorerPanel;

	private WorkspaceSearchPanel workspaceSearchPanel;

	private SettingsPanel settingsPanel;

	private DebugConsolePanel debugConsolePanel;

	private KeybindingManager keybindingManager;

	private JSplitPane mainSplit;

	private JSplitPane editorBottomSplit;

	private boolean sidebarVisible = true;

	private final Object trayLock = new Object();

	private TrayIcon trayIcon;

	private AutoCloseable statusNotifierTray;

	private boolean trayDisposed;

	/**
	 * Builds and displays the application window, including its sidebar, editors,
	 * terminal, output and debug console.
	 */
	@Override
	public void run() {
		setTitle("SIMPLE-IDE");
		ApplicationIcon.applyTo(this);

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowNode().onClosing(this::handleWindowClosing).onClosed(this::handleWindowClosed));

		LogPanel logPanel = new LogPanel();
		treePane = new JScrollPane();
		JScrollPane logPane = new JScrollPane(logPanel);
		LspStatusBar lspStatusBar = new LspStatusBar();

		TerminalPanel terminalPanel = new TerminalPanel();
		terminalController = new BusyBoxTerminalController(terminalPanel);

		debugConsolePanel = new DebugConsolePanel();
		JTabbedPane outputTabs = new JTabbedPane();
		outputTabs.addTab("Output", logPane);
		outputTabs.addTab("Debug Console", debugConsolePanel);
		outputTabs.addTab("Terminal", terminalPanel);

		editorManager = new EditorManager(this::createNewFile, this::createNewDirectory, this::openFolder,
				lspStatusBar::updateStatus);
		editorManager.addSelectedTabListener(debugConsolePanel::showTab);
		editorManager.addDiagnosticsListener(debugConsolePanel::refresh);

		createSidebar();

		editorBottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorManager.getJTabbedPane(), outputTabs);
		editorBottomSplit.setResizeWeight(0.78);
		editorBottomSplit.setContinuousLayout(true);

		mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeftColumn(), editorBottomSplit);
		mainSplit.setResizeWeight(0.0);
		mainSplit.setContinuousLayout(true);

		MainLayout mainLayout = new MainLayout();
		mainLayout.addComponent(mainSplit, 0, 0, 1, 1, 1.0, 1.0);
		mainLayout.addComponent(lspStatusBar, 0, 1, 1, 1, 1.0, 0.0);
		setLayout(mainLayout);
		add(mainSplit);
		add(lspStatusBar);

		attachLogAppender(logPanel);

		IdeSettings settings = SettingsService.getInstance().getSettings();
		keybindingManager = new KeybindingManager(settings);
		installKeybindings();
		MenuBarFactory.createMenuBar(this, this::createNewFile, this::createNewDirectory, this::openFolder,
				this::closeFolder, editorManager::saveSelectedFile);
		ThemeManager.getInstance().register(this);
		applySettings(settings);
		SettingsService.getInstance().addListener(this::applySettings);
		logger.info("start IDE");

		pack();
		setInitialWindowSize();
		setVisible(true);
		installTrayIcon();
		mainSplit.setDividerLocation(activityBar.getPreferredSize().width + SIDEBAR_WIDTH + 12);
		editorBottomSplit.setDividerLocation(0.78);
	}

	private void createSidebar() {
		explorerPanel = new ExplorerPanel(treePane, this::openFolder);
		workspaceSearchPanel = new WorkspaceSearchPanel(() -> currentRoot, editorManager::getOpenFilePaths,
				this::openSearchMatch);
		settingsPanel = new SettingsPanel();

		sidebarPanel = new SidebarPanel();
		sidebarPanel.addView(SidebarView.EXPLORER, explorerPanel);
		sidebarPanel.addView(SidebarView.SEARCH, workspaceSearchPanel);
		sidebarPanel.addView(SidebarView.SETTINGS, settingsPanel);
		sidebarPanel.showView(SidebarView.EXPLORER);

		activityBar = new ActivityBar();
		activityBar.setSelectionListener(this::showSidebarView);
	}

	private JPanel createLeftColumn() {
		JPanel leftColumn = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics graphics) {
				Graphics2D rounded = (Graphics2D) graphics.create();
				try {
					rounded.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					rounded.setColor(UIManager.getColor("Panel.background"));
					rounded.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 18, 18));
				}
				finally {
					rounded.dispose();
				}
				super.paintComponent(graphics);
			}
		};
		leftColumn.setOpaque(false);
		leftColumn.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		leftColumn.add(activityBar, BorderLayout.WEST);
		leftColumn.add(sidebarPanel, BorderLayout.CENTER);
		return leftColumn;
	}

	private void handleWindowClosing(@SuppressWarnings("unused") WindowEvent event) {
		if (editorManager != null && !editorManager.saveAllModifiedFiles()) {
			JOptionPane.showMessageDialog(this,
					"Unable to save one or more modified files. Check the log before closing.", "Save failed",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (terminalController == null) {
			dispose();
			return;
		}
		try {
			terminalController.onWindowClosing(this::dispose);
		}
		catch (RuntimeException exception) {
			logger.error("Unable to close the terminal sessions cleanly.", exception);
			dispose();
		}
	}

	private void handleWindowClosed(@SuppressWarnings("unused") WindowEvent event) {
		try {
			closeWatcher();
			if (editorManager != null) {
				editorManager.closeAll();
			}
			ThreadMaster.getInstance().shutdown();
		}
		finally {
			removeTrayIcon();
		}
	}

	private void installKeybindings() {
		Map<IdeAction, Runnable> actions = new EnumMap<>(IdeAction.class);
		actions.put(IdeAction.NEW_FILE, this::createNewFile);
		actions.put(IdeAction.OPEN_FOLDER, this::openFolder);
		actions.put(IdeAction.CLOSE_FOLDER, this::closeFolder);
		actions.put(IdeAction.CLOSE_EDITOR, () -> editorManager.closeSelectedTab());
		actions.put(IdeAction.CLOSE_ALL_EDITORS, () -> editorManager.closeAllEditors());
		actions.put(IdeAction.SAVE_ALL, () -> editorManager.saveAllEditors());
		actions.put(IdeAction.TOGGLE_SIDEBAR, this::toggleSidebar);
		actions.put(IdeAction.WORKSPACE_SEARCH, () -> showSidebarView(SidebarView.SEARCH));
		actions.put(IdeAction.OPEN_SETTINGS, () -> showSidebarView(SidebarView.SETTINGS));
		keybindingManager.install(getRootPane(), actions);
	}

	private void applySettings(IdeSettings settings) {
		if (settings == null) {
			return;
		}
		ThemeManager themeManager = ThemeManager.getInstance();
		themeManager.setFonts(settings.getUiFontFamily(), settings.getUiFontSize(), settings.getEditorFontFamily(),
				settings.getEditorFontSize());
		ThemeManager.ThemeType theme = IdeSettings.parseTheme(settings.getTheme());
		themeManager.setTheme(theme == null ? ThemeManager.ThemeType.DARK : theme);
		if (editorManager != null) {
			editorManager.applyEditorOptions(settings.getTabSize(), settings.isWordWrap(), settings.isShowLineNumbers(),
					settings.isShowWhitespace());
		}
		if (keybindingManager != null) {
			keybindingManager.reload(settings);
			installKeybindings();
		}
		if (getJMenuBar() instanceof IdeMenuBar menuBar) {
			menuBar.applyKeybindings(settings);
		}
		if (activityBar != null) {
			activityBar.refreshTheme();
		}
	}

	private void showSidebarView(SidebarView view) {
		sidebarPanel.showView(view);
		activityBar.setSelected(view);
		sidebarVisible = true;
		sidebarPanel.setVisible(true);
		if (view == SidebarView.SEARCH) {
			workspaceSearchPanel.focusSearchField();
		}
		sidebarPanel.revalidate();
		sidebarPanel.repaint();
	}

	private void toggleSidebar() {
		sidebarVisible = !sidebarVisible;
		sidebarPanel.setVisible(sidebarVisible);
		sidebarPanel.revalidate();
		sidebarPanel.repaint();
	}

	private void installTrayIcon() {
		synchronized (trayLock) {
			if (trayDisposed || trayIcon != null || statusNotifierTray != null) {
				return;
			}
		}
		// Icon loading and D-Bus registration can block, so keep both away from the
		// EDT and never leave the frame unpainted while the desktop tray is contacted.
		ThreadMaster.getInstance().register(new RunnableNode(this::installTrayIconInBackground));
	}

	private void installTrayIconInBackground() {
		// Use the full-resolution source so high-density StatusNotifierItem pixmaps are
		// downscaled instead of being enlarged from the small AWT tray image.
		java.awt.Image iconImage = ApplicationIcon.loadImage();
		if (iconImage == null) {
			logger.warn("The SIMPLE-IDE application icon is missing; no tray icon can be installed.");
			return;
		}
		if (isLinux()) {
			try {
				AutoCloseable notifier = LinuxStatusNotifierTray.install((java.awt.image.BufferedImage) iconImage,
						this::showWindow);
				if (notifier != null) {
					synchronized (trayLock) {
						if (!trayDisposed) {
							statusNotifierTray = notifier;
							logger.info("SIMPLE-IDE registered a StatusNotifierItem tray icon.");
							return;
						}
					}
					closeTray(notifier);
					return;
				}
			}
			catch (org.freedesktop.dbus.exceptions.DBusException | RuntimeException error) {
				logger.warn("Unable to register SIMPLE-IDE with the Linux StatusNotifierWatcher; trying AWT.", error);
			}
		}
		installAwtTrayIcon(iconImage);
	}

	private void installAwtTrayIcon(java.awt.Image iconImage) {
		try {
			if (!SystemTray.isSupported()) {
				logger.warn("This desktop does not expose a compatible system tray for SIMPLE-IDE.");
				return;
			}

			PopupMenu menu = new PopupMenu();
			MenuItem show = new MenuItem("Show SIMPLE-IDE");
			show.addActionListener(new ActionNode(event -> showWindow()));
			menu.add(show);
			MenuItem quit = new MenuItem("Quit SIMPLE-IDE");
			quit.addActionListener(new ActionNode(event -> requestClose()));
			menu.add(quit);
			TrayIcon icon = new TrayIcon(iconImage, "SIMPLE-IDE", menu);
			icon.setImageAutoSize(true);
			icon.addActionListener(new ActionNode(event -> showWindow()));
			SystemTray tray = SystemTray.getSystemTray();
			tray.add(icon);
			synchronized (trayLock) {
				if (!trayDisposed) {
					trayIcon = icon;
					logger.info("SIMPLE-IDE registered an AWT system tray icon.");
					return;
				}
			}
			tray.remove(icon);
		}
		catch (java.awt.AWTException | RuntimeException error) {
			logger.warn("Unable to add SIMPLE-IDE to the system tray.", error);
		}
	}

	private void removeTrayIcon() {
		AutoCloseable notifier;
		TrayIcon icon;
		synchronized (trayLock) {
			trayDisposed = true;
			notifier = statusNotifierTray;
			statusNotifierTray = null;
			icon = trayIcon;
			trayIcon = null;
		}
		if (icon != null) {
			try {
				if (SystemTray.isSupported()) {
					SystemTray.getSystemTray().remove(icon);
				}
			}
			catch (RuntimeException error) {
				logger.warn("Unable to remove SIMPLE-IDE from the AWT system tray.", error);
			}
		}
		if (notifier != null) {
			closeTray(notifier);
		}
	}

	private void closeTray(AutoCloseable tray) {
		try {
			tray.close();
		}
		catch (Exception error) {
			logger.warn("Unable to remove SIMPLE-IDE from the system tray.", error);
		}
	}

	private void showWindow() {
		ThreadMaster.getInstance().executeOnEDT(() -> {
			setVisible(true);
			setState(JFrame.NORMAL);
			toFront();
		});
	}

	private void requestClose() {
		ThreadMaster.getInstance().executeOnEDT(() -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
	}

	private boolean isLinux() {
		return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux");
	}

	private void setInitialWindowSize() {
		Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		int width = Math.min(1440, usable.width);
		int height = Math.min(810, usable.height);
		// Preserve a 16:9 content area where the available screen permits it.
		if (usable.width / 16.0 < usable.height / 9.0) {
			height = Math.min(usable.height, (int) Math.round(width * 9.0 / 16.0));
		}
		else {
			width = Math.min(usable.width, (int) Math.round(height * 16.0 / 9.0));
		}
		setMinimumSize(new Dimension(Math.min(800, usable.width), Math.min(450, usable.height)));
		setSize(Math.max(getMinimumSize().width, width), Math.max(getMinimumSize().height, height));
		setLocationRelativeTo(null);
	}

	private void openFolder() {
		Path start = currentRoot != null ? currentRoot : Path.of(System.getProperty("user.dir"));
		JFileChooser chooser = new JFileChooser(start.toFile());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Open Folder");
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		loadFolder(chooser.getSelectedFile().toPath());
	}

	private void loadFolder(Path path) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					Directory root = new Directory(path);
					threadMaster.executeOnEDT(() -> showFolder(root));
				}
				catch (IOException error) {
					logger.error("Unable to load project directory {}.", path, error);
					threadMaster.executeOnEDT(
							() -> new ErrorPopUP(error.getMessage(), getRootPane(), ErrorTypePopUp.INTERNAL_ERROR)
								.startManaged());
				}
			}));
		}
		catch (RuntimeException error) {
			logger.error("Unable to schedule loading project directory {}.", path, error);
		}
	}

	private void showFolder(Directory root) {
		if (tree == null) {
			tree = new Tree(root);
			tree.setRootChangeListener(this::updateWatcherRoot);
			tree.addTreeSelectionListener(new TreeSelectionNode(this::onTreeSelection));
		}
		else {
			tree.setRoot(root);
		}
		currentRoot = root.getPath().toAbsolutePath().normalize();
		explorerPanel.showTree(tree);
		updateWatcherRoot(root);
		showSidebarView(SidebarView.EXPLORER);
	}

	private void onTreeSelection(TreeSelectionEvent event) {
		TreePath selectedPath = event.getNewLeadSelectionPath();
		if (selectedPath == null) {
			return;
		}
		Object selectedNode = selectedPath.getLastPathComponent();
		if (selectedNode instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof File selectedFile) {
			editorManager.openFile(selectedFile);
		}
	}

	private void updateWatcherRoot(Directory root) {
		if (tree == null) {
			return;
		}
		try {
			if (watcher == null) {
				watcher = new Watcher(tree);
				watcher.start(root.getPath());
			}
			else {
				watcher.setRoot(root.getPath());
			}
		}
		catch (IOException | IllegalArgumentException error) {
			logger.error("Unable to start file watcher for {}.", root.getPath(), error);
		}
	}

	private void closeWatcher() {
		if (watcher != null) {
			watcher.close();
			watcher = null;
		}
	}

	private void closeFolder() {
		if (tree == null && currentRoot == null) {
			return;
		}
		if (editorManager != null) {
			editorManager.closeAllEditors();
		}
		closeWatcher();
		tree = null;
		currentRoot = null;
		treePane.setViewportView(null);
		explorerPanel.showNoFolder();
		showSidebarView(SidebarView.EXPLORER);
	}

	private void createNewFile() {
		if (tree != null) {
			tree.createNewFile();
			return;
		}

		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("New File");
		chooser.setSelectedFile(new java.io.File("untitled.txt"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		Path path = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		if (Files.exists(path) && JOptionPane.showConfirmDialog(this, "Overwrite " + path.getFileName() + "?",
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
					File file = new File(path);
					threadMaster.executeOnEDT(() -> {
						if (parent != null && (currentRoot == null || !path.startsWith(currentRoot))) {
							loadFolder(parent);
						}
						editorManager.openFile(file);
					});
				}
				catch (IOException error) {
					logger.error("Unable to create file {}.", path, error);
					threadMaster.executeOnEDT(
							() -> new ErrorPopUP("Unable to create " + path.getFileName() + ": " + error.getMessage(),
									getRootPane(), ErrorTypePopUp.INTERNAL_ERROR)
								.startManaged());
				}
			}));
		}
		catch (RuntimeException error) {
			logger.error("Unable to schedule creating file {}.", path, error);
		}
	}

	private void createNewDirectory() {
		if (tree == null) {
			new InfoPopUP("Information", "Open a folder before creating a new folder.", getRootPane()).startManaged();
			return;
		}
		tree.createNewDirectory();
	}

	private void openSearchMatch(Path path, int line) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					File file = new File(path);
					threadMaster.executeOnEDT(() -> {
						editorManager.openFile(file);
						EditorTab tab = editorManager.getOpenedEditor(path.toAbsolutePath().normalize());
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
	 * Returns the editor selected in the application editor manager.
	 * @return the selected editor, or {@code null} when no editor manager exists or no
	 * tab is selected
	 */
	public static Editor getEditor() {
		EditorManager manager = EditorManager.getInstance();
		return manager == null ? null : manager.getSelectedEditor();
	}

	private void attachLogAppender(LogPanel logPanel) {
		configureTerminalLoggers();
		Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		SwingLogAppender appender = new SwingLogAppender(logPanel);
		appender.setContext(rootLogger.getLoggerContext());
		appender.start();
		rootLogger.addAppender(appender);
	}

	private void configureTerminalLoggers() {
		((Logger) LoggerFactory.getLogger("com.jediterm")).setLevel(Level.INFO);
		((Logger) LoggerFactory.getLogger("com.pty4j")).setLevel(Level.INFO);
	}

}
