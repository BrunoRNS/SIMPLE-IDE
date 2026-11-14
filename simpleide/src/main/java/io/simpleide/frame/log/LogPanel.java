package io.simpleide.frame.log;

import io.simpleide.thread.ThreadMaster;
import java.awt.Color;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogPanel extends JTextPane {

  private static final Logger LOGGER = LoggerFactory.getLogger(LogPanel.class);

  public LogPanel() {
    setEditable(false);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    Color background = UIManager.getColor("TextPane.background");
    Color foreground = UIManager.getColor("TextPane.foreground");
    if (background != null) {
      setBackground(background);
      applyDocumentBackground(background);
    }
    if (foreground != null) {
      setForeground(foreground);
    }
  }

  public void appendEntry(String text, String level, Color color) {
    ThreadMaster.getInstance().executeOnEDT(() -> appendEntryOnEdt(text, level, color));
  }

  private void appendEntryOnEdt(String text, String level, Color color) {
    StyledDocument doc = getStyledDocument();

    SimpleAttributeSet levelStyle = new SimpleAttributeSet();
    StyleConstants.setForeground(levelStyle, Color.BLACK.equals(color) ? getForeground() : color);
    StyleConstants.setBackground(levelStyle, getBackground());

    SimpleAttributeSet messageStyle = new SimpleAttributeSet();
    StyleConstants.setForeground(messageStyle, getForeground());
    StyleConstants.setBackground(messageStyle, getBackground());

    try {
      doc.insertString(doc.getLength(), "[" + level + "]", levelStyle);
      doc.insertString(doc.getLength(), text + "\n", messageStyle);
      setCaretPosition(doc.getLength());
    } catch (BadLocationException exception) {
      LOGGER.error("Unable to append an entry to the IDE log panel.", exception);
    }
  }

  private void applyDocumentBackground(Color background) {
    if (!(getDocument() instanceof StyledDocument document) || document.getLength() == 0) {
      return;
    }
    SimpleAttributeSet style = new SimpleAttributeSet();
    StyleConstants.setBackground(style, background);
    document.setCharacterAttributes(0, document.getLength(), style, false);
  }
}
