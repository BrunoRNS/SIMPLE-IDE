package io.simpleide.frame.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SwingLogAppenderTest {

  @Test
  void appendShouldFormatAndDisplayLogEntry() throws Exception {
    LogPanel logPanel = new LogPanel();
    SwingLogAppender appender = new SwingLogAppender(logPanel);

    LoggingEvent event = new LoggingEvent();
    event.setLevel(Level.ERROR);
    event.setLoggerName("MyLogger");
    event.setMessage("algo deu errado");

    appender.append(event);
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(logPanel.getText())
        .isEqualToNormalizingNewlines("[ERROR][MyLogger] algo deu errado\n");
  }
}
