package io.simpleide.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link io.simpleide.settings.SettingsService}.
 */
class SettingsServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void updateWritesJsonAndReloadsTheStoredValues() throws IOException {
		Path file = tempDir.resolve(".simpleide").resolve("settings.json");
		SettingsService service = new SettingsService(file);

		IdeSettings updated = service.update(settings -> {
			settings.setTheme("LIGHT");
			settings.setUiFontSize(15);
			settings.setEditorFontSize(16);
			settings.setTabSize(2);
			settings.setWordWrap(true);
			settings.setShowWhitespace(true);
			settings.setShowLineNumbers(false);
			settings.setKeybinding(IdeAction.NEW_FILE, "ctrl shift N");
		});

		assertThat(updated.getTheme()).isEqualTo("LIGHT");
		assertThat(file).exists();
		assertThat(Files.readString(file)).contains("\"theme\"").contains("LIGHT");

		IdeSettings reloaded = new SettingsService(file).getSettings();

		assertThat(reloaded.getTheme()).isEqualTo("LIGHT");
		assertThat(reloaded.getUiFontSize()).isEqualTo(15);
		assertThat(reloaded.getEditorFontSize()).isEqualTo(16);
		assertThat(reloaded.getTabSize()).isEqualTo(2);
		assertThat(reloaded.isWordWrap()).isTrue();
		assertThat(reloaded.isShowWhitespace()).isTrue();
		assertThat(reloaded.isShowLineNumbers()).isFalse();
		assertThat(reloaded.keybindingOf(IdeAction.NEW_FILE)).isEqualTo("ctrl shift N");
	}

	@Test
	void missingFileFallsBackToDefaults() {
		SettingsService service = new SettingsService(tempDir.resolve("missing.json"));

		IdeSettings settings = service.getSettings();

		assertThat(settings.getTheme()).isEqualTo("DARK");
		assertThat(settings.getTabSize()).isEqualTo(IdeSettings.DEFAULT_TAB_SIZE);
		assertThat(settings.keybindingOf(IdeAction.TOGGLE_SIDEBAR)).isEqualTo("ctrl B");
	}

	@Test
	void corruptFileFallsBackToDefaults() throws IOException {
		Path file = tempDir.resolve("settings.json");
		Files.writeString(file, "{ this is not json");

		IdeSettings settings = new SettingsService(file).getSettings();

		assertThat(settings.getTheme()).isEqualTo("DARK");
	}

	@Test
	void resetToDefaultsStoresDefaultValuesAgain() {
		Path file = tempDir.resolve("settings.json");
		SettingsService service = new SettingsService(file);
		service.update(settings -> {
			settings.setTheme("DRACULA");
			settings.setTabSize(8);
		});

		IdeSettings defaults = service.resetToDefaults();

		assertThat(defaults.getTheme()).isEqualTo("DARK");
		assertThat(defaults.getTabSize()).isEqualTo(IdeSettings.DEFAULT_TAB_SIZE);
		assertThat(new SettingsService(file).getSettings().getTheme()).isEqualTo("DARK");
	}

}
