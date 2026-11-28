package io.simpleide.ui.sidebar;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
	@SuppressWarnings("unused")
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

	@Test
	void settingsSpinnersPutTheirControlsOnTheLeftAndAlignValuesToTheLeft() throws Exception {
		List<JSpinner> spinners = new ArrayList<>();
		List<ComponentOrientation> orientations = new ArrayList<>();
		List<Integer> buttonLeftEdges = new ArrayList<>();
		List<Integer> editorLeftEdges = new ArrayList<>();
		List<Integer> textAlignments = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			SettingsPanel panel = new SettingsPanel(new SettingsService(tempDir.resolve("spinner-settings.json")));
			findSpinners(panel, spinners);
			for (JSpinner spinner : spinners) {
				spinner.setSize(240, spinner.getPreferredSize().height);
				layoutRecursively(spinner);
				orientations.add(spinner.getComponentOrientation());
				buttonLeftEdges.add(leftEdgeOf(spinner, JButton.class));
				editorLeftEdges.add(leftEdgeOf(spinner, JFormattedTextField.class));
				textAlignments
					.add(((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getHorizontalAlignment());
			}
		});

		assertThat(spinners).hasSize(3);
		for (int index = 0; index < spinners.size(); index++) {
			assertThat(orientations.get(index)).isEqualTo(ComponentOrientation.RIGHT_TO_LEFT);
			assertThat(buttonLeftEdges.get(index)).isLessThan(editorLeftEdges.get(index));
			assertThat(textAlignments.get(index)).isEqualTo(JFormattedTextField.LEFT);
		}
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

	private static void findSpinners(Component component, List<JSpinner> spinners) {
		if (component instanceof JSpinner spinner) {
			spinners.add(spinner);
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				findSpinners(child, spinners);
			}
		}
	}

	private static int leftEdgeOf(Container container, Class<? extends Component> type) {
		int left = Integer.MAX_VALUE;
		for (Component child : container.getComponents()) {
			if (type.isInstance(child)) {
				assertNotNull(child);
				left = Math.min(left, child.getX());
			}
			if (child instanceof Container nested) {
				int nestedLeft = leftEdgeOf(nested, type);
				if (nestedLeft != Integer.MAX_VALUE) {
					left = Math.min(left, child.getX() + nestedLeft);
				}
			}
		}
		return left;
	}

	private static void layoutRecursively(Container container) {
		container.doLayout();
		for (Component child : container.getComponents()) {
			if (child instanceof Container nested) {
				layoutRecursively(nested);
			}
		}
	}

}
