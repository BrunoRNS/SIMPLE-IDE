package io.simpleide.frame.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link io.simpleide.frame.log.LogPanel}.
 */
class LogPanelTest {

	@Test
	void logPanelShouldNotBeEditable() {
		LogPanel logPanel = new LogPanel();
		assertThat(logPanel.isEditable()).isEqualTo(false);
	}

	@Test
	void appendEntryShouldApplyLevelColor() throws Exception {
		LogPanel log = new LogPanel();
		Color messageColor = new Color(0xF4E7D7);
		Color levelColor = new Color(0xFF5555);
		log.setForeground(messageColor);
		log.appendEntry("hello", "ERROR", levelColor);

		flushEdt();

		StyledDocument doc = log.getStyledDocument();

		Element levelEl = doc.getCharacterElement(0);
		assertThat(StyleConstants.getForeground(levelEl.getAttributes())).isEqualTo(levelColor);
		assertThat(StyleConstants.getBackground(levelEl.getAttributes())).isEqualTo(log.getBackground());

		Element msgEl = doc.getCharacterElement(7);
		assertThat(StyleConstants.getForeground(msgEl.getAttributes())).isEqualTo(messageColor);
		assertThat(StyleConstants.getBackground(msgEl.getAttributes())).isEqualTo(log.getBackground());
	}

	@Test
	void missingLevelColorShouldUseThemeForeground() throws Exception {
		LogPanel log = new LogPanel();
		Color themeForeground = new Color(0xEBDBB2);
		log.setForeground(themeForeground);
		log.appendEntry("hello", "INFO", null);

		flushEdt();

		Element levelElement = log.getStyledDocument().getCharacterElement(0);
		assertThat(StyleConstants.getForeground(levelElement.getAttributes())).isEqualTo(themeForeground);
	}

	@Test
	void updateUiShouldRefreshExistingEntriesWithTheNewThemeColors() throws Exception {
		Color originalBackground = UIManager.getColor("TextPane.background");
		Color originalForeground = UIManager.getColor("TextPane.foreground");
		LogPanel[] logReference = new LogPanel[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				logReference[0] = new LogPanel();
				logReference[0].appendEntry("hello", "INFO", null);
			});
			flushEdt();

			SwingUtilities.invokeAndWait(() -> {
				Color background = new Color(0x1E1E1E);
				Color foreground = new Color(0xCCCCCC);
				UIManager.put("TextPane.background", background);
				UIManager.put("TextPane.foreground", foreground);
				LogPanel log = logReference[0];
				SwingUtilities.updateComponentTreeUI(log);

				assertThat(log.getBackground()).isEqualTo(background);
				assertThat(log.getForeground()).isEqualTo(foreground);
				Element levelElement = log.getStyledDocument().getCharacterElement(0);
				assertThat(StyleConstants.getForeground(levelElement.getAttributes())).isEqualTo(foreground);
				assertThat(StyleConstants.getBackground(levelElement.getAttributes())).isEqualTo(background);
			});
		}
		finally {
			restoreUiValue("TextPane.background", originalBackground);
			restoreUiValue("TextPane.foreground", originalForeground);
		}
	}

	private static void flushEdt() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
		});
	}

	private static void restoreUiValue(String key, Object value) {
		if (value == null) {
			UIManager.getDefaults().remove(key);
		}
		else {
			UIManager.put(key, value);
		}
	}

}
