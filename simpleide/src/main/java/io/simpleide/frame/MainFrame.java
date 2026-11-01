package io.simpleide.frame;

import javax.swing.JFrame;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;

public class MainFrame extends JFrame implements Runnable {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MainFrame.class);

  @Override
  public void run() {

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    LogPanel logPanel = new LogPanel();
    add(logPanel);
    attachLogAppender(logPanel);

    logger.info("start IDE");

    pack();
    setVisible(true);
  }

  private void attachLogAppender(LogPanel logPanel) {
    Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    SwingLogAppender appender = new SwingLogAppender(logPanel);
    appender.setContext(rootLogger.getLoggerContext());
    appender.start();
    rootLogger.addAppender(appender);
  }

}
