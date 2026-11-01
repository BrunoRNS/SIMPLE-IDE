package io.simpleide.frame.log;

import java.awt.Color;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class SwingLogAppender extends AppenderBase<ILoggingEvent> {

  private final LogPanel logPanel;

  public SwingLogAppender(LogPanel logPanel) {
    this.logPanel = logPanel;
  }

  @Override
  protected void append(ILoggingEvent event) {
    Color color = LogLevelColorMapping.colorForLevel(event.getLevel());
    logPanel.appendEntry(format(event), event.getLevel().levelStr, color);

  }

  private String format(ILoggingEvent event) {
    return String.format("[%s] %s",
        event.getLoggerName(),
        event.getFormattedMessage());
  }

}
