package io.simpleide.frame;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import io.simpleide.frame.layout.MainLayout;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;
import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.menubar.MenuBarFactory;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.TreeFileFactory;

public class MainFrame extends JFrame implements Runnable {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrame.class);
  private Tree tree;

  @Override
  public void run() {

    setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    setLocationRelativeTo(null);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent event) {
        ThreadMaster.getInstance().shutdown();
      }
    });

    LogPanel logPanel = new LogPanel();
    JScrollPane treePane = new JScrollPane();
    JScrollPane logPane = new JScrollPane(logPanel);
    MainLayout mainLayout = new MainLayout();

    mainLayout.addComponent(treePane, 0, 0, 1, 3, 1.0, 1.0);
    add(treePane);

    mainLayout.addComponent(logPane, 0, 4, 3, 1, 1.0, 1.0);
    add(logPane);

    attachLogAppender(logPanel);
    setLayout(mainLayout);
    ThemeManager.getInstance().register(this);
    MenuBarFactory.createMenuBar(this, this::createNewFile, this::createNewDirectory, this::setDirectory);
    logger.info("start IDE");
    pack();
    setLocationRelativeTo(null);
    setVisible(true);
    loadTree(treePane);
  }

  private void loadTree(JScrollPane treePane) {
    ThreadMaster threadMaster = ThreadMaster.getInstance();
    threadMaster.register(new RunnableNode(() -> {
      try {
        Directory root = TreeFileFactory.loadRootDirectory();
        threadMaster.executeOnEDT(() -> {
          tree = new Tree(root);
          treePane.setViewportView(tree);
          treePane.revalidate();
          treePane.repaint();
        });
      } catch (IOException exception) {
        logger.error("Unable to load the project tree.", exception);
      }
    }));
  }

  private void createNewFile() {
    if (tree == null) {
      JOptionPane.showMessageDialog(this, "The project tree is still loading.");
      return;
    }
    tree.createNewFile();
  }

  private void createNewDirectory() {
    if (tree == null) {
      JOptionPane.showMessageDialog(this, "The project tree is still loading.");
      return;
    }
    tree.createNewDirectory();
  }

  private void setDirectory() {
    if (tree == null) {
      JOptionPane.showMessageDialog(this, "The project tree is still loading.");
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
