package io.simpleide.frame.log;

import java.awt.Color;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;

public class LogPanel extends JTextPane {

  private static final Logger LOGGER = LoggerFactory.getLogger(LogPanel.class);

  public LogPanel() {
    setEditable(false);
  }

  public void appendEntry(String text, String level, Color color) {
    ThreadMaster.getInstance().executeOnEDT(() -> appendEntryOnEdt(text, level, color));
  }

  private void appendEntryOnEdt(String text, String level, Color color) {
    StyledDocument doc = getStyledDocument();

    SimpleAttributeSet levelStyle = new SimpleAttributeSet();
    StyleConstants.setForeground(levelStyle, color);

    SimpleAttributeSet messageStyle = new SimpleAttributeSet();
    StyleConstants.setForeground(messageStyle, Color.BLACK);

    try {
      doc.insertString(doc.getLength(), "[" + level + "]", levelStyle);
      doc.insertString(doc.getLength(), text + "\n", messageStyle);
      setCaretPosition(doc.getLength());
    } catch (BadLocationException exception) {
      LOGGER.error("Unable to append an entry to the IDE log panel.", exception);
    }
  }

}
