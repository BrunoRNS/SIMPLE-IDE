package io.simpleide.settings;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link io.simpleide.settings.IdeSettings} and
 * {@link io.simpleide.settings.KeybindingManager}.
 */
class IdeSettingsTest {

	@Test
	void normalizeReplacesInvalidValuesWithDefaults() {
		IdeSettings settings = new IdeSettings();
		settings.setTheme("unknown");
		settings.setUiFontSize(2);
		settings.setEditorFontSize(200);
		settings.setTabSize(0);
		settings.setKeybindings(null);

		settings.normalize();

		assertThat(settings.getTheme()).isEqualTo("DARK");
		assertThat(settings.getUiFontSize()).isEqualTo(IdeSettings.DEFAULT_UI_FONT_SIZE);
		assertThat(settings.getEditorFontSize()).isEqualTo(IdeSettings.DEFAULT_EDITOR_FONT_SIZE);
		assertThat(settings.getTabSize()).isEqualTo(IdeSettings.DEFAULT_TAB_SIZE);
		assertThat(settings.keybindingOf(IdeAction.NEW_FILE)).isEqualTo(IdeAction.NEW_FILE.getDefaultKeybinding());
	}

	@Test
	void customKeybindingOverridesDefaultUntilBlank() {
		IdeSettings settings = new IdeSettings();

		settings.setKeybinding(IdeAction.NEW_FILE, "ctrl shift N");

		assertThat(settings.keybindingOf(IdeAction.NEW_FILE)).isEqualTo("ctrl shift N");

		settings.setKeybinding(IdeAction.NEW_FILE, " ");

		assertThat(settings.keybindingOf(IdeAction.NEW_FILE)).isEqualTo(IdeAction.NEW_FILE.getDefaultKeybinding());
	}

	@Test
	void applyKeybindingRejectsTextThatCannotBeParsed() {
		IdeSettings settings = new IdeSettings();

		boolean applied = KeybindingManager.applyKeybinding(settings, IdeAction.NEW_FILE, "definitely not a key");

		assertThat(applied).isFalse();
		assertThat(settings.keybindingOf(IdeAction.NEW_FILE)).isEqualTo(IdeAction.NEW_FILE.getDefaultKeybinding());
	}

	@Test
	void applyKeybindingStoresValidTextAndBlankRestoresDefault() {
		IdeSettings settings = new IdeSettings();

		assertThat(KeybindingManager.applyKeybinding(settings, IdeAction.OPEN_FOLDER, "ctrl alt O")).isTrue();
		assertThat(settings.keybindingOf(IdeAction.OPEN_FOLDER)).isEqualTo("ctrl alt O");

		assertThat(KeybindingManager.applyKeybinding(settings, IdeAction.OPEN_FOLDER, "")).isTrue();
		assertThat(settings.keybindingOf(IdeAction.OPEN_FOLDER))
			.isEqualTo(IdeAction.OPEN_FOLDER.getDefaultKeybinding());
	}

	@Test
	void formatKeyStrokeProducesParseableText() {
		KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S,
				InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);

		String text = IdeAction.formatKeyStroke(keyStroke);

		assertThat(IdeAction.parseKeyStroke(text)).isNotNull();
		assertThat(text).contains("ctrl").contains("shift").contains("S");
	}

}
