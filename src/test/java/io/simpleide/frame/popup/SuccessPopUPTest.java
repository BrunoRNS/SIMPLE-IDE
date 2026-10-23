package io.simpleide.frame.popup;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.Collections;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

public class SuccessPopUPTest {

    @Test
    public void createsPopupWithSuccessMessageAndIcon() {
        SuccessPopUP popup = new SuccessPopUP(
                "Success", "File saved successfully", null, "Ok");

        assertThat(popup.getTitle()).isEqualTo("Success");
        assertThat(popup.jp.getMessage()).isEqualTo("File saved successfully");
        assertThat(popup.jp.getMessageType()).isEqualTo(JOptionPane.INFORMATION_MESSAGE);
        assertThat(popup.jp.getOptionType()).isEqualTo(JOptionPane.DEFAULT_OPTION);
        assertThat(popup.jp.getOptions()).containsExactly("Ok");
        assertThat(popup.jp.getInitialValue()).isEqualTo("Ok");
        assertThat(popup.jp.getIcon())
                .isInstanceOfSatisfying(ImageIcon.class, icon -> {
                    assertThat(icon.getIconWidth()).isPositive();
                    assertThat(icon.getIconHeight()).isPositive();
                });
    }

    @Test
    public void acceptsParentComponentInConstructor() {
        JComponent parent = new JPanel();

        SuccessPopUP popup = new SuccessPopUP("Success", "Operation completed", parent, "Close");

        assertThat(popup.parent).isSameAs(parent);
        assertThat(popup.getTitle()).isEqualTo("Success");
        assertThat(popup.jp.getInitialValue()).isEqualTo("Close");
    }

    @Test
    public void mapsAndUpdatesTitle() {
        SuccessPopUP popup = new SuccessPopUP("Success", "Completed", null, "Ok");

        assertThat(popup.getMap()).containsEntry("title", "Success");

        popup.setMap(Collections.<String, Object>singletonMap("title", "Updated"));

        assertThat(popup.getTitle()).isEqualTo("Updated");
    }

    @Test
    public void addsSuccessIconToOptionPaneForRendering() {
        SuccessPopUP popup = new SuccessPopUP("Success", "Completed", null, "Ok");

        assertThat(containsIcon(popup.jp, popup.jp.getIcon())).isTrue();
    }

    private static boolean containsIcon(Container container, Icon expectedIcon) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel
                    && ((JLabel) component).getIcon() == expectedIcon) {
                return true;
            }
            if (component instanceof Container
                    && containsIcon((Container) component, expectedIcon)) {
                return true;
            }
        }
        return false;
    }
}
