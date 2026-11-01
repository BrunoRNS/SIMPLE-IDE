package io.simpleide.frame.log;

import java.awt.Color;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class LogPanel extends JTextPane {

  public LogPanel() {
    setEditable(false);
  }

  public void appendEntry(String text, String level, Color color) {
    SwingUtilities.invokeLater(() -> {
      StyledDocument doc = getStyledDocument();

      SimpleAttributeSet levelStyle = new SimpleAttributeSet();
      StyleConstants.setForeground(levelStyle, color);

      SimpleAttributeSet messageStyle = new SimpleAttributeSet();
      StyleConstants.setForeground(messageStyle, Color.BLACK);

      try {
        doc.insertString(doc.getLength(), "[" + level + "]", levelStyle);
        doc.insertString(doc.getLength(), text + "\n", messageStyle);
        setCaretPosition(doc.getLength());
      } catch (BadLocationException e) {
        e.printStackTrace();
      }
    });
  }

}
