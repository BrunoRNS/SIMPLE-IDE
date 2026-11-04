package io.simpleide.frame;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import io.simpleide.frame.layout.MainLayout;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;
import io.simpleide.ui.menubar.MenuBarFactory;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.TreeFileFactory;

public class MainFrame extends JFrame implements Runnable {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrame.class);

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
    MenuBarFactory.createMenuBar(this);
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
          treePane.setViewportView(new Tree(root));
          treePane.revalidate();
          treePane.repaint();
        });
      } catch (IOException exception) {
        logger.error("Unable to load the project tree.", exception);
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
