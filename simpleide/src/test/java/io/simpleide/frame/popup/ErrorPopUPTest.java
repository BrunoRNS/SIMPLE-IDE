package io.simpleide.frame.popup;

import java.awt.Component;
import java.awt.Container;
import java.util.Collections;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.simpleide.frame.popup.utils.ErrorTypePopUp;

/**
 * Tests {@link io.simpleide.frame.popup.ErrorPopUP}.
 */
class ErrorPopUPTest {

	@Test
	void createsPopupWithErrorMessageTypeAndIcon() {
		ErrorPopUP popup = new ErrorPopUP("Failed to compile", ErrorTypePopUp.COMPILE_ERROR);

		assertThat(popup.getTitle()).isEqualTo("Compile error");
		assertThat(popup.jp.getMessage()).isEqualTo("Failed to compile");
		assertThat(popup.jp.getMessageType()).isEqualTo(JOptionPane.ERROR_MESSAGE);
		assertThat(popup.jp.getOptionType()).isEqualTo(JOptionPane.DEFAULT_OPTION);
		assertThat(popup.jp.getOptions()).containsExactly("Ok");
		assertThat(popup.jp.getInitialValue()).isEqualTo("Ok");
		assertThat(popup.jp.getIcon()).isInstanceOfSatisfying(ImageIcon.class, icon -> {
			assertThat(icon.getIconWidth()).isPositive();
			assertThat(icon.getIconHeight()).isPositive();
		});
	}

	@Test
	void acceptsParentComponentInConstructor() {
		JComponent parent = new JPanel();

		ErrorPopUP popup = new ErrorPopUP("Erro", parent, ErrorTypePopUp.RUNTIME_ERROR);

		assertThat(popup.parent).isSameAs(parent);
		assertThat(popup.getTitle()).isEqualTo("Runtime error");
	}

	@Test
	void mapsAndUpdatesTitle() {
		ErrorPopUP popup = new ErrorPopUP("Erro", ErrorTypePopUp.INTERNAL_ERROR);

		assertThat(popup.getMap()).containsEntry("title", "Internal error");

		popup.setMap(Collections.<String, Object>singletonMap("title", "Update error"));

		assertThat(popup.getTitle()).isEqualTo("Update error");
	}

	@Test
	void addsErrorIconToOptionPaneForRendering() {
		ErrorPopUP popup = new ErrorPopUP("eror fatal", ErrorTypePopUp.FATAL_ERROR);

		assertThat(containsIcon(popup.jp, popup.jp.getIcon())).isTrue();
	}

	private static boolean containsIcon(Container container, Icon expectedIcon) {
		for (Component component : container.getComponents()) {
			if (component instanceof JLabel && ((JLabel) component).getIcon() == expectedIcon) {
				return true;
			}
			if (component instanceof Container && containsIcon((Container) component, expectedIcon)) {
				return true;
			}
		}
		return false;
	}

}
