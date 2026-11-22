package io.simpleide.ui.sidebar;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Duration;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.settings.SettingsService;
import io.simpleide.ui.menubar.ThemeManager;

class SettingsPanelTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void resetTheme() throws Exception {
		SwingUtilities.invokeAndWait(() -> ThemeManager.getInstance().setTheme(ThemeManager.ThemeType.DARK));
	}

	@Test
	void selectingThemeAppliesAndPersistsIt() throws Exception {
		SettingsService settingsService = new SettingsService(tempDir.resolve("settings.json"));
		SettingsPanel[] panel = new SettingsPanel[1];

		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new SettingsPanel(settingsService);
			JComboBox<?> selector = themeSelector(panel[0]);
			assertThat(selector).isNotNull();
			selector.setSelectedItem(ThemeManager.ThemeType.LIGHT);
		});

		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			assertThat(settingsService.getSettings().getTheme()).isEqualTo("LIGHT");
			assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(ThemeManager.ThemeType.LIGHT);
		});
	}

	private static JComboBox<?> themeSelector(Component component) {
		if (component instanceof JComboBox<?> comboBox && comboBox.getItemAt(0) instanceof ThemeManager.ThemeType) {
			return comboBox;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JComboBox<?> result = themeSelector(child);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

}
