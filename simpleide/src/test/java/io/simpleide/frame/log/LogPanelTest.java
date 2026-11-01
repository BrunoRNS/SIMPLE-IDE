package io.simpleide.frame.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;

import javax.swing.SwingUtilities;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.junit.jupiter.api.Test;

class LogPanelTest {

  @Test
  public void logPanelShouldNotBeEditable() {
    LogPanel logPanel = new LogPanel();
    assertThat(logPanel.isEditable()).isEqualTo(false);
  }

  @Test
  public void appendEntryShouldApplyLevelColor() throws Exception {
    LogPanel log = new LogPanel();
    log.appendEntry("hello", "ERROR", Color.RED);

    SwingUtilities.invokeAndWait(() -> {
    });

    StyledDocument doc = log.getStyledDocument();

    Element levelEl = doc.getCharacterElement(0);
    assertThat(StyleConstants.getForeground(levelEl.getAttributes()))
        .isEqualTo(Color.RED);

    Element msgEl = doc.getCharacterElement(7);
    assertThat(StyleConstants.getForeground(msgEl.getAttributes()))
        .isEqualTo(Color.BLACK);
  }
}
