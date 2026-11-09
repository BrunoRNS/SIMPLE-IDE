package io.simpleide.frame;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import io.simpleide.frame.layout.MainLayout;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;
import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.InfoPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorManager;
import io.simpleide.ui.menubar.MenuBarFactory;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.TreeFileFactory;
import io.simpleide.ui.treefile.Watcher;

public class MainFrame extends JFrame implements Runnable {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrame.class);
  private Tree tree;
  private Watcher watcher;
  private EditorManager editorManager;

  @Override
  public void run() {

    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setLocationRelativeTo(null);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent event) {
        if (watcher != null) {
          watcher.close();
        }
        if (editorManager != null) {
          editorManager.closeAll();
        }
        ThreadMaster.getInstance().shutdown();
      }
    });

    LogPanel logPanel = new LogPanel();
    JScrollPane treePane = new JScrollPane();
    JScrollPane logPane = new JScrollPane(logPanel);

    editorManager = new EditorManager(this::createNewFile, this::createNewDirectory, this::setDirectory);

    JSplitPane editorTreePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePane, editorManager.getJTabbedPane());
    editorTreePane.setResizeWeight(0.2);
    editorTreePane.setContinuousLayout(true);

    JSplitPane mainVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorTreePane, logPane);
    mainVerticalSplit.setResizeWeight(0.8);
    mainVerticalSplit.setContinuousLayout(true);

    MainLayout mainLayout = new MainLayout();
    mainLayout.addComponent(mainVerticalSplit, 0, 0, 1, 1, 1.0, 1.0);
    setLayout(mainLayout);
    add(mainVerticalSplit);

    attachLogAppender(logPanel);

    ThemeManager.getInstance().register(this);
    MenuBarFactory.createMenuBar(
        this, this::createNewFile, this::createNewDirectory, this::setDirectory, editorManager::saveSelectedFile);
    logger.info("start IDE");

    pack();
    setLocationRelativeTo(null);
    setVisible(true);

    editorTreePane.setDividerLocation(0.2);
    mainVerticalSplit.setDividerLocation(0.8);

    loadTree(treePane);
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
      } catch (IOException exception) {
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
    } catch (IOException | IllegalArgumentException error) {
      logger.error("Unable to start file watcher for {}.", root.getPath(), error);
    }
  }

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
      } catch (IOException exception) {
        logger.error("Unable to load project directory {}.", path, exception);
        threadMaster.executeOnEDT(() -> new ErrorPopUP(
            exception.getMessage(), getRootPane(), ErrorTypePopUp.INTERNAL_ERROR).startManaged());
      }
    }));
  }

  private void attachLogAppender(LogPanel logPanel) {
    Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    SwingLogAppender appender = new SwingLogAppender(logPanel);
    appender.setContext(rootLogger.getLoggerContext());
    appender.start();
    rootLogger.addAppender(appender);
  }

}
