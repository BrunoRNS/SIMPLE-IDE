package io.simpleide.frame;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.simpleide.frame.layout.MainLayout;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;
import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.InfoPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.frame.utils.ApplicationIcon;
import io.simpleide.frame.utils.LinuxStatusNotifierTray;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorManager;
import io.simpleide.ui.editor.LspStatusBar;
import io.simpleide.ui.menubar.MenuBarFactory;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.TreeFileFactory;
import io.simpleide.ui.treefile.Watcher;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.PopupMenu;
import java.awt.MenuItem;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.LoggerFactory;

/**
 * Main desktop window of Simple IDE.
 *
 * <p>
 * The frame wires the project tree, editor manager, terminal, log console, status bar and
 * menu bar together. It implements {@link Runnable} so it can be created on the Swing
 * Event Dispatch Thread by an {@code EDTNode}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 */
public class MainFrame extends JFrame implements Runnable {

	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrame.class);

	private Tree tree;

	private Watcher watcher;

	private EditorManager editorManager;

	private BusyBoxTerminalController terminalController;

	private final Object trayLock = new Object();

	private TrayIcon trayIcon;

	private AutoCloseable statusNotifierTray;

	private boolean trayDisposed;

	/**
	 * Builds and displays the application window, including its tree, editors, terminal
	 * and log console.
	 */
	@Override
	public void run() {
		setTitle("SIMPLE-IDE");
		ApplicationIcon.applyTo(this);

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setLocationRelativeTo(null);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				if (editorManager != null && !editorManager.saveAllModifiedFiles()) {
					javax.swing.JOptionPane.showMessageDialog(MainFrame.this,
							"Unable to save one or more modified files. Check the log before closing.", "Save failed",
							javax.swing.JOptionPane.ERROR_MESSAGE);
					return;
				}
				if (terminalController == null) {
					dispose();
					return;
				}
				try {
					terminalController.onWindowClosing(MainFrame.this::dispose);
				}
				catch (RuntimeException exception) {
					logger.error("Unable to close the terminal sessions cleanly.", exception);
					dispose();
				}
			}

			@Override
			public void windowClosed(WindowEvent event) {
				try {
					if (watcher != null) {
						watcher.close();
					}
					if (editorManager != null) {
						editorManager.closeAll();
					}
					ThreadMaster.getInstance().shutdown();
				}
				finally {
					removeTrayIcon();
				}
			}
		});

		LogPanel logPanel = new LogPanel();
		JScrollPane treePane = new JScrollPane();
		JScrollPane logPane = new JScrollPane(logPanel);
		LspStatusBar lspStatusBar = new LspStatusBar();

		TerminalPanel terminalPanel = new TerminalPanel();
		terminalController = new BusyBoxTerminalController(terminalPanel);
		JTabbedPane outputTabs = new JTabbedPane();
		outputTabs.addTab("Debug Console", logPane);
		outputTabs.addTab("Terminal", terminalPanel);

		editorManager = new EditorManager(this::createNewFile, this::createNewDirectory, this::setDirectory,
				lspStatusBar::updateStatus);

		JSplitPane editorTreePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePane,
				editorManager.getJTabbedPane());
		editorTreePane.setResizeWeight(0.2);
		editorTreePane.setContinuousLayout(true);

		JSplitPane mainVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorTreePane, outputTabs);
		mainVerticalSplit.setResizeWeight(0.8);
		mainVerticalSplit.setContinuousLayout(true);

		MainLayout mainLayout = new MainLayout();
		mainLayout.addComponent(mainVerticalSplit, 0, 0, 1, 1, 1.0, 1.0);
		mainLayout.addComponent(lspStatusBar, 0, 1, 1, 1, 1.0, 0.0);
		setLayout(mainLayout);
		add(mainVerticalSplit);
		add(lspStatusBar);

		attachLogAppender(logPanel);

		MenuBarFactory.createMenuBar(this, this::createNewFile, this::createNewDirectory, this::setDirectory,
				editorManager::saveSelectedFile);
		ThemeManager.getInstance().register(this);
		logger.info("start IDE");

		pack();
		setInitialWindowSize();
		setVisible(true);
		installTrayIcon();
		javax.swing.SwingUtilities.invokeLater(() -> {
			editorTreePane.setDividerLocation(0.2);
			mainVerticalSplit.setDividerLocation(0.8);
		});

		loadTree(treePane);
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
			show.addActionListener(event -> showWindow());
			menu.add(show);
			MenuItem quit = new MenuItem("Quit SIMPLE-IDE");
			quit.addActionListener(event -> requestClose());
			menu.add(quit);
			TrayIcon icon = new TrayIcon(iconImage, "SIMPLE-IDE", menu);
			icon.setImageAutoSize(true);
			icon.addActionListener(event -> showWindow());
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
		javax.swing.SwingUtilities.invokeLater(() -> {
			setVisible(true);
			setState(JFrame.NORMAL);
			toFront();
		});
	}

	private void requestClose() {
		javax.swing.SwingUtilities.invokeLater(() -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
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

	private void loadTree(JScrollPane treePane) {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.register(new RunnableNode(() -> {
			try {
				Directory root = TreeFileFactory.loadRootDirectory();
				threadMaster.executeOnEDT(() -> {
					tree = new Tree(root);
					tree.setRootChangeListener(this::updateWatcherRoot);
					updateWatcherRoot(root);
					tree.addTreeSelectionListener(event -> {
						TreePath selectedPath = event.getNewLeadSelectionPath();
						if (selectedPath == null) {
							return;
						}
						Object selectedNode = selectedPath.getLastPathComponent();
						if (selectedNode instanceof DefaultMutableTreeNode node
								&& node.getUserObject() instanceof File selectedFile) {
							editorManager.openFile(selectedFile);
						}
					});
					treePane.setViewportView(tree);
					treePane.revalidate();
					treePane.repaint();
				});
			}
			catch (IOException exception) {
				logger.error("Unable to load the project tree.", exception);
			}
		}));
	}

	private void updateWatcherRoot(Directory root) {
		if (watcher != null) {
			watcher.setRoot(root.getPath());
			return;
		}

		try {
			watcher = new Watcher(tree);
			watcher.start(root.getPath());
		}
		catch (IOException | IllegalArgumentException error) {
			logger.error("Unable to start file watcher for {}.", root.getPath(), error);
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

	private void createNewFile() {
		if (tree == null) {
			new InfoPopUP("Information", "The project tree is still loading.", getRootPane()).startManaged();
			return;
		}
		tree.createNewFile();
	}

	private void createNewDirectory() {
		if (tree == null) {
			new InfoPopUP("Information", "The project tree is still loading.", getRootPane()).startManaged();
			return;
		}
		tree.createNewDirectory();
	}

	private void setDirectory() {
		if (tree == null) {
			new InfoPopUP("Information", "The project tree is still loading.", getRootPane()).startManaged();
			return;
		}

		JFileChooser chooser = new JFileChooser(tree.getState().getCurrentDirectory().getPath().toFile());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		Path path = chooser.getSelectedFile().toPath();
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.register(new RunnableNode(() -> {
			try {
				Directory root = new Directory(path);
				threadMaster.executeOnEDT(() -> tree.setRoot(root));
			}
			catch (IOException exception) {
				logger.error("Unable to load project directory {}.", path, exception);
				threadMaster.executeOnEDT(
						() -> new ErrorPopUP(exception.getMessage(), getRootPane(), ErrorTypePopUp.INTERNAL_ERROR)
							.startManaged());
			}
		}));
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
