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

/**
 * Read-only text pane that displays colored log entries for the application.
 *
 * <p>
 * Entries are appended on the Swing Event Dispatch Thread, and the pane background is
 * kept in sync with the active look and feel.
 *
 * <p>
 * Covered by {@code LogPanelTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/logging.md">docs/api/ui/logging.md</a>
 */
public class LogPanel extends JTextPane {

	private static final Logger LOGGER = LoggerFactory.getLogger(LogPanel.class);

	/**
	 * Creates an empty read-only log panel.
	 */
	public LogPanel() {
		setEditable(false);
	}

	/**
	 * Refreshes the look-and-feel colors, including the existing document background.
	 */
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

	/**
	 * Appends a log entry, scheduling the update on the Swing Event Dispatch Thread.
	 * @param text message text
	 * @param level level label shown in brackets
	 * @param color level color; black uses the current foreground color
	 */
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
		}
		catch (BadLocationException exception) {
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
