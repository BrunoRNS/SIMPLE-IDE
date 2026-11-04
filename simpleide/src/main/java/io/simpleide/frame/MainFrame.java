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
    MainLayout leftLayout = new MainLayout();
    JPanel leftPane = new JPanel(leftLayout);
    leftPane.setPreferredSize(new Dimension(320, 600));
    leftLayout.addComponent(treePane, 0, 0, 1, 1, 1.0, 0.7);
    leftLayout.addComponent(logPane, 0, 1, 1, 1, 1.0, 0.3);
    leftPane.add(treePane);
    leftPane.add(logPane);

    JPanel editorPane = new JPanel();
    editorPane.setPreferredSize(new Dimension(700, 600));
    MainLayout mainLayout = new MainLayout();
    JPanel mainPane = new JPanel(mainLayout);
    mainLayout.addComponent(leftPane, 0, 0, 1, 1, 0.3, 1.0);
    mainLayout.addComponent(editorPane, 1, 0, 1, 1, 0.7, 1.0);
    mainPane.add(leftPane);
    mainPane.add(editorPane);
    setContentPane(mainPane);

    attachLogAppender(logPanel);

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
