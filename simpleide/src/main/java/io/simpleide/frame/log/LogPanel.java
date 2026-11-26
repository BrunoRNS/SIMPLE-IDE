package io.simpleide.frame.log;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;

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
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/logging.md">docs/api/ui/logging.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class LogPanel extends JTextPane {

	private static final Logger LOGGER = LoggerFactory.getLogger(LogPanel.class);

	private final List<LogEntry> entries = new ArrayList<>();

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
		}
		if (foreground != null) {
			setForeground(foreground);
		}
		rebuildDocument();
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
		LogEntry entry = new LogEntry(text, level, color);
		entries.add(entry);
		insertEntry(entry);
	}

	private void insertEntry(LogEntry entry) {
		StyledDocument doc = getStyledDocument();

		SimpleAttributeSet levelStyle = new SimpleAttributeSet();
		StyleConstants.setForeground(levelStyle, levelColor(entry.color()));
		StyleConstants.setBackground(levelStyle, getBackground());

		SimpleAttributeSet messageStyle = new SimpleAttributeSet();
		StyleConstants.setForeground(messageStyle, getForeground());
		StyleConstants.setBackground(messageStyle, getBackground());

		try {
			doc.insertString(doc.getLength(), "[" + entry.level() + "]", levelStyle);
			doc.insertString(doc.getLength(), entry.text() + "\n", messageStyle);
			setCaretPosition(doc.getLength());
		}
		catch (BadLocationException exception) {
			LOGGER.error("Unable to append an entry to the IDE log panel.", exception);
		}
	}

	private Color levelColor(Color preferred) {
		return preferred == null ? getForeground() : preferred;
	}

	private void rebuildDocument() {
		if (entries == null || entries.isEmpty() || !(getDocument() instanceof StyledDocument document)) {
			return;
		}
		try {
			document.remove(0, document.getLength());
		}
		catch (BadLocationException exception) {
			LOGGER.warn("Unable to refresh the IDE log panel colors.", exception);
			return;
		}
		for (LogEntry entry : entries) {
			insertEntry(entry);
		}
	}

	/**
	 * Applies a background color directly to the characters already present in the log
	 * document.
	 *
	 * <p>
	 * This method is kept for legacy integrations that called it directly. New code must
	 * let {@link #rebuildDocument()} re-render the stored entries with the active theme,
	 * which also keeps the entry colors consistent.
	 * @param background background color to apply
	 * @deprecated since v1.1.0, use the themed look and feel together with
	 * {@link #rebuildDocument()} instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void applyDocumentBackground(Color background) {
		if (!(getDocument() instanceof StyledDocument document) || document.getLength() == 0) {
			return;
		}
		SimpleAttributeSet style = new SimpleAttributeSet();
		StyleConstants.setBackground(style, background);
		document.setCharacterAttributes(0, document.getLength(), style, false);
	}

	private record LogEntry(String text, String level, Color color) {
	}

}
