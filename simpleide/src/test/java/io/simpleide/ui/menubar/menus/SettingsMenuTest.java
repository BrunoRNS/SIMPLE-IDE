package io.simpleide.ui.menubar.menus;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Duration;

import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.SettingsService;

class SettingsMenuTest {

	@TempDir
	Path tempDir;

	@Test
	void interfaceAndEditorFontSizesUpdateIndependentlyAndPersist() throws Exception {
		SettingsService settingsService = new SettingsService(tempDir.resolve("settings.json"));
		SettingsMenu[] menu = new SettingsMenu[1];
		SwingUtilities.invokeAndWait(() -> {
			menu[0] = new SettingsMenu(settingsService);
			spinner(menu[0], "uiFontSizeSpinner").setValue(20);
		});

		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			IdeSettings settings = settingsService.getSettings();
			assertThat(settings.getUiFontSize()).isEqualTo(20);
			assertThat(settings.getEditorFontSize()).isEqualTo(IdeSettings.DEFAULT_EDITOR_FONT_SIZE);
		});

		SwingUtilities.invokeAndWait(() -> spinner(menu[0], "editorFontSizeSpinner").setValue(26));
		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			IdeSettings settings = settingsService.getSettings();
			assertThat(settings.getUiFontSize()).isEqualTo(20);
			assertThat(settings.getEditorFontSize()).isEqualTo(26);
		});

		IdeSettings persisted = new SettingsService(settingsService.getSettingsPath()).getSettings();
		assertThat(persisted.getUiFontSize()).isEqualTo(20);
		assertThat(persisted.getEditorFontSize()).isEqualTo(26);
	}

	@Test
	void alignsLabelsBesideCompactSpinners() throws Exception {
		SettingsMenu[] menu = new SettingsMenu[1];
		SwingUtilities.invokeAndWait(() -> {
			menu[0] = new SettingsMenu(new SettingsService(tempDir.resolve("layout-settings.json")));
			JPanel interfaceRow = findNamedComponent(menu[0], "interfaceFontSizeRow", JPanel.class);
			JPanel editorRow = findNamedComponent(menu[0], "editorFontSizeRow", JPanel.class);
			JLabel interfaceLabel = findNamedComponent(menu[0], "interfaceFontSizeRowLabel", JLabel.class);
			JLabel editorLabel = findNamedComponent(menu[0], "editorFontSizeRowLabel", JLabel.class);
			JSpinner interfaceSpinner = spinner(menu[0], "uiFontSizeSpinner");
			JSpinner editorSpinner = spinner(menu[0], "editorFontSizeSpinner");

			int rowWidth = Math.max(interfaceRow.getPreferredSize().width, editorRow.getPreferredSize().width);
			interfaceRow.setSize(rowWidth, interfaceRow.getPreferredSize().height);
			editorRow.setSize(rowWidth, editorRow.getPreferredSize().height);
			interfaceRow.doLayout();
			editorRow.doLayout();

			assertThat(interfaceLabel.getParent()).isSameAs(interfaceRow);
			assertThat(editorLabel.getParent()).isSameAs(editorRow);
			assertThat(interfaceSpinner.getX()).isGreaterThan(interfaceLabel.getX() + interfaceLabel.getWidth());
			assertThat(editorSpinner.getX()).isGreaterThan(editorLabel.getX() + editorLabel.getWidth());
			assertThat(interfaceLabel.getX()).isEqualTo(editorLabel.getX());
			assertThat(interfaceSpinner.getX()).isEqualTo(editorSpinner.getX());
			assertThat(interfaceSpinner.getPreferredSize().width).isLessThan(80);
			assertThat(editorSpinner.getPreferredSize().width).isLessThan(80);
			assertThat(interfaceRow.isOpaque()).isFalse();
			assertThat(editorRow.isOpaque()).isFalse();
		});
	}

	private static JSpinner spinner(SettingsMenu menu, String name) {
		JSpinner spinner = findNamedComponent(menu, name, JSpinner.class);
		if (spinner == null) {
			throw new AssertionError("Font size spinner not found: " + name);
		}
		return spinner;
	}

	private static <T extends Component> T findNamedComponent(Container parent, String name, Class<T> type) {
		Component[] children = parent instanceof JMenu menu ? menu.getMenuComponents() : parent.getComponents();
		for (Component component : children) {
			if (type.isInstance(component) && name.equals(component.getName())) {
				return type.cast(component);
			}
			if (component instanceof Container child) {
				T found = findNamedComponent(child, name, type);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

}
