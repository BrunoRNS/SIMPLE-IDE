package io.simpleide.ui.io;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.OutputType;
import io.simpleide.ui.ThemeColors;

/** Applies the active theme to the I/O tab's text components. */
/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class IOOutputStyler {

	private static final Logger LOGGER = LoggerFactory.getLogger(IOOutputStyler.class);

	private IOOutputStyler() {
	}

	static SimpleAttributeSet styleFor(OutputType type) {
		SimpleAttributeSet style = new SimpleAttributeSet();
		Color color = switch (type) {
			case ERROR -> ThemeColors.ui("Log.errorForeground", "Component.error.focusedBorderColor");
			case DEBUG -> ThemeColors.ui("Log.debugForeground", "Component.focusColor");
			case OUTPUT -> null;
		};
		if (color != null) {
			StyleConstants.setForeground(style, color);
		}
		return style;
	}

	static void appendLine(JTextPane outputPane, OutputType type, String text) {
		java.util.Objects.requireNonNull(type, "type");
		if (text == null) {
			return;
		}
		Runnable append = () -> {
			StyledDocument document = outputPane.getStyledDocument();
			try {
				document.insertString(document.getLength(), "[" + type + "] " + text + System.lineSeparator(),
						styleFor(type));
				outputPane.setCaretPosition(document.getLength());
			}
			catch (BadLocationException error) {
				LOGGER.warn("Unable to append output to the I/O tab.", error);
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			append.run();
		}
		else {
			SwingUtilities.invokeLater(append);
		}
	}

	static void applyTheme(JTextArea inputArea, JTextPane outputPane) {
		Color background = ThemeColors.ui("TextArea.background", "EditorPane.background", "Panel.background");
		Color foreground = ThemeColors.ui("TextArea.foreground", "EditorPane.foreground", "Label.foreground");
		Font font = UIManager.getFont("TextArea.font");
		if (background != null) {
			inputArea.setBackground(background);
			outputPane.setBackground(background);
		}
		if (foreground != null) {
			inputArea.setForeground(ThemeColors.readable(foreground, background));
			outputPane.setForeground(ThemeColors.readable(foreground, background));
		}
		if (font != null) {
			Font mono = new Font(Font.MONOSPACED, font.getStyle(), font.getSize());
			inputArea.setFont(mono);
			outputPane.setFont(mono);
		}
		inputArea.setCaretColor(foreground);
		inputArea.setSelectionColor(ThemeColors.ui("TextArea.selectionBackground", "EditorPane.selectionBackground"));
		inputArea
			.setSelectedTextColor(ThemeColors.ui("TextArea.selectionForeground", "EditorPane.selectionForeground"));
		inputArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
	}

	static void refreshOutputStyles(JTextPane outputPane) {
		StyledDocument document = outputPane.getStyledDocument();
		if (document.getLength() == 0) {
			return;
		}
		try {
			String text = document.getText(0, document.getLength());
			document.remove(0, document.getLength());
			for (String line : text.split("(?<=\\n)", -1)) {
				OutputType type = line.startsWith("[ERROR] ") ? OutputType.ERROR
						: line.startsWith("[DEBUG] ") ? OutputType.DEBUG : OutputType.OUTPUT;
				document.insertString(document.getLength(), line, styleFor(type));
			}
		}
		catch (BadLocationException ignored) {
			LOGGER.warn("Unable to refresh output colors after a theme change.", ignored);
		}
	}

}
