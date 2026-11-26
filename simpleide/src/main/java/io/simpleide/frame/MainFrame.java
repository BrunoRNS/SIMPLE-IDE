package io.simpleide.frame;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.layout.MainLayout;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;
import io.simpleide.frame.utils.ApplicationIcon;
import io.simpleide.settings.IdeAction;
import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.KeybindingManager;
import io.simpleide.settings.SettingsService;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.WindowNode;
import io.simpleide.ui.debug.DebugConsolePanel;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorManager;
import io.simpleide.ui.editor.LspStatusBar;
import io.simpleide.ui.io.IOPanel;
import io.simpleide.ui.menubar.MenuBarFactory;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.menubar.menus.IdeMenuBar;
import io.simpleide.ui.sidebar.ActivityBar;
import io.simpleide.ui.sidebar.ExplorerPanel;
import io.simpleide.ui.sidebar.GitPanel;
import io.simpleide.ui.sidebar.SettingsPanel;
import io.simpleide.ui.sidebar.SidebarPanel;
import io.simpleide.ui.sidebar.SidebarView;
import io.simpleide.ui.sidebar.WorkspaceSearchPanel;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.Watcher;

/**
 * Main desktop window of Simple IDE.
 *
 * <p>
 * The frame wires the activity bar and its Explorer, Search and Settings views, the
 * editor manager, the Output, Debug Console, Terminal and I/O tabs, the status bar and
 * the menu bar together. It implements {@link Runnable} so it can be created on the Swing
 * Event Dispatch Thread by an {@code EDTNode}; every listener is routed through the
 * thread manager and every shortcut and settings change is persisted under
 * {@code ~/.simpleide/settings.json}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class MainFrame extends JFrame implements Runnable {

	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrame.class);

	private static final int SIDEBAR_WIDTH = 300;

	Tree tree;

	Watcher watcher;

	volatile java.nio.file.Path currentRoot;

	EditorManager editorManager;

	private BusyBoxTerminalController terminalController;

	JScrollPane treePane;

	private SidebarPanel sidebarPanel;

	private ActivityBar activityBar;

	ExplorerPanel explorerPanel;

	WorkspaceSearchPanel workspaceSearchPanel;

	GitPanel gitPanel;

	private SettingsPanel settingsPanel;

	private DebugConsolePanel debugConsolePanel;

	IOPanel ioPanel;

	JTabbedPane outputTabs;

	KeybindingManager keybindingManager;

	private JSplitPane mainSplit;

	private JSplitPane editorBottomSplit;

	private boolean sidebarVisible = true;

	private int mainSplitDividerSize;

	private final MainFrameTrayController trayController = new MainFrameTrayController(this::showWindow,
			this::requestClose);

	private final MainFrameWorkspaceController workspaceController;

	private final MainFrameExecutionController executionController;

	/**
	 * Creates the main window. Swing components are built by {@link #run()} on the event
	 * dispatch thread.
	 */
	public MainFrame() {
		this.workspaceController = new MainFrameWorkspaceController(this);
		this.executionController = new MainFrameExecutionController(this);
	}

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
		terminalController = new BusyBoxTerminalController(terminalPanel,
				workspaceController::terminalWorkingDirectory);

		debugConsolePanel = new DebugConsolePanel();
		ioPanel = new IOPanel();
		outputTabs = new JTabbedPane();
		outputTabs.addTab("Output", logPane);
		outputTabs.addTab("Debug Console", debugConsolePanel);
		outputTabs.addTab("Terminal", terminalPanel);
		outputTabs.addTab("I/O", ioPanel);

		editorManager = new EditorManager(workspaceController::createNewFile, workspaceController::createNewDirectory,
				workspaceController::openFolder, lspStatusBar::updateStatus);
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
		executionController.configureDebugAvailability();
		installKeybindings();
		MenuBarFactory.createMenuBar(this, workspaceController::createNewFile, workspaceController::createNewDirectory,
				workspaceController::openFolder, workspaceController::closeFolder, editorManager::saveSelectedFile,
				executionController::runProgram, executionController::debugProgram);
		executionController.applyDebugAvailability();
		ThemeManager.getInstance().register(this);
		applySettings(settings);
		SettingsService.getInstance().addListener(this::applySettings);
		logger.info("start IDE");

		pack();
		setInitialWindowSize();
		setVisible(true);
		trayController.install();
		mainSplit.setDividerLocation(activityBar.getPreferredSize().width + SIDEBAR_WIDTH + 12);
		editorBottomSplit.setDividerLocation(0.78);
	}

	private void createSidebar() {
		explorerPanel = new ExplorerPanel(treePane, workspaceController::openFolder);
		workspaceSearchPanel = new WorkspaceSearchPanel(() -> currentRoot, editorManager::getOpenFilePaths,
				workspaceController::openSearchMatch);
		gitPanel = new GitPanel();
		settingsPanel = new SettingsPanel();

		sidebarPanel = new SidebarPanel();
		gitPanel.setFileOpenListener(workspaceController::openGitFile);
		sidebarPanel.addView(SidebarView.EXPLORER, explorerPanel);
		sidebarPanel.addView(SidebarView.SEARCH, workspaceSearchPanel);
		sidebarPanel.addView(SidebarView.GIT, gitPanel);
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
			executionController.close();
			workspaceController.closeWatcher();
			if (editorManager != null) {
				editorManager.closeAll();
			}
			ThreadMaster.getInstance().shutdown();
		}
		finally {
			trayController.close();
		}
	}

	void installKeybindings() {
		Map<IdeAction, Runnable> actions = new EnumMap<>(IdeAction.class);
		actions.put(IdeAction.NEW_FILE, workspaceController::createNewFile);
		actions.put(IdeAction.OPEN_FOLDER, workspaceController::openFolder);
		actions.put(IdeAction.CLOSE_FOLDER, workspaceController::closeFolder);
		actions.put(IdeAction.CLOSE_EDITOR, () -> editorManager.closeSelectedTab());
		actions.put(IdeAction.CLOSE_ALL_EDITORS, () -> editorManager.closeAllEditors());
		actions.put(IdeAction.SAVE_ALL, () -> editorManager.saveAllEditors());
		actions.put(IdeAction.TOGGLE_SIDEBAR, this::toggleSidebar);
		actions.put(IdeAction.WORKSPACE_SEARCH, () -> showSidebarView(SidebarView.SEARCH));
		actions.put(IdeAction.OPEN_SETTINGS, () -> showSidebarView(SidebarView.SETTINGS));
		actions.put(IdeAction.RUN, executionController::runProgram);
		if (executionController.isDebugAvailable()) {
			actions.put(IdeAction.DEBUG, executionController::debugProgram);
		}
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

	void showSidebarView(SidebarView view) {
		sidebarPanel.showView(view);
		activityBar.setSelected(view);
		if (!sidebarVisible) {
			setSidebarVisible(true);
		}
		if (view == SidebarView.SEARCH) {
			workspaceSearchPanel.focusSearchField();
		}
		sidebarPanel.revalidate();
		sidebarPanel.repaint();
	}

	private void toggleSidebar() {
		setSidebarVisible(!sidebarVisible);
	}

	private void setSidebarVisible(boolean visible) {
		sidebarVisible = visible;
		if (visible) {
			sidebarPanel.setMinimumSize(new Dimension(180, 0));
			sidebarPanel.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
			sidebarPanel.setVisible(true);
			mainSplit.setDividerSize(mainSplitDividerSize);
			mainSplit.setDividerLocation(activityBar.getPreferredSize().width + SIDEBAR_WIDTH + 12);
		}
		else {
			mainSplitDividerSize = mainSplit.getDividerSize();
			sidebarPanel.setVisible(false);
			sidebarPanel.setMinimumSize(new Dimension(0, 0));
			sidebarPanel.setPreferredSize(new Dimension(0, 0));
			mainSplit.setDividerSize(0);
			mainSplit.setDividerLocation(activityBar.getPreferredSize().width + 12);
		}
		mainSplit.revalidate();
		mainSplit.repaint();
		sidebarPanel.revalidate();
		sidebarPanel.repaint();
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
