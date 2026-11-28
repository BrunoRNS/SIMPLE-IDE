package io.simpleide.ui.sidebar;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests Source Control input hints.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
class GitPanelTest {

	@Test
	void labelsLocalGitIdentityFieldsWithPlaceholders() throws Exception {
		List<String> placeholders = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> collectPlaceholders(new GitPanel(), placeholders));

		assertThat(placeholders).contains("user.name", "user.email");
	}

	private static void collectPlaceholders(Container container, List<String> placeholders) {
		for (Component child : container.getComponents()) {
			if (child instanceof JTextField field) {
				Object placeholder = field.getClientProperty("JTextField.placeholderText");
				if (placeholder instanceof String text) {
					placeholders.add(text);
				}
			}
			if (child instanceof Container nested) {
				collectPlaceholders(nested, placeholders);
			}
		}
	}

}
