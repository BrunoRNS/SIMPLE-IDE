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

public class WarningPopUPTest {

  @Test
  public void createsPopupWithWarningMessageAndIcon() {
    WarningPopUP popup = new WarningPopUP("Warning", "This action cannot be undone", "Continue");

    assertThat(popup.getTitle()).isEqualTo("Warning");
    assertThat(popup.jp.getMessage()).isEqualTo("This action cannot be undone");
    assertThat(popup.jp.getMessageType()).isEqualTo(JOptionPane.WARNING_MESSAGE);
    assertThat(popup.jp.getOptionType()).isEqualTo(JOptionPane.DEFAULT_OPTION);
    assertThat(popup.jp.getOptions()).containsExactly("Ok");
    assertThat(popup.jp.getInitialValue()).isEqualTo("Continue");
    assertThat(popup.jp.getIcon())
        .isInstanceOfSatisfying(
            ImageIcon.class,
            icon -> {
              assertThat(icon.getIconWidth()).isPositive();
              assertThat(icon.getIconHeight()).isPositive();
            });
  }

  @Test
  public void acceptsParentAndCustomOptionInConstructor() {
    JComponent parent = new JPanel();

    WarningPopUP popup = new WarningPopUP("Warning", "Unsaved changes", parent, "Review");

    assertThat(popup.parent).isSameAs(parent);
    assertThat(popup.getTitle()).isEqualTo("Warning");
    assertThat(popup.jp.getInitialValue()).isEqualTo("Review");
  }

  @Test
  public void usesOkAsDefaultOption() {
    WarningPopUP popup = new WarningPopUP("Warning", "Please check your input");

    assertThat(popup.jp.getInitialValue()).isEqualTo("Ok");
    assertThat(popup.jp.getOptions()).containsExactly("Ok");
  }

  @Test
  public void acceptsParentWithDefaultOption() {
    JComponent parent = new JPanel();

    WarningPopUP popup = new WarningPopUP("Warning", "Review the details", parent);

    assertThat(popup.parent).isSameAs(parent);
    assertThat(popup.jp.getInitialValue()).isEqualTo("Ok");
  }

  @Test
  public void mapsAndUpdatesTitle() {
    WarningPopUP popup = new WarningPopUP("Warning", "Check the details");

    assertThat(popup.getMap()).containsEntry("title", "Warning");

    popup.setMap(Collections.<String, Object>singletonMap("title", "Updated warning"));

    assertThat(popup.getTitle()).isEqualTo("Updated warning");
  }

  @Test
  public void addsWarningIconToOptionPaneForRendering() {
    WarningPopUP popup = new WarningPopUP("Warning", "Check the details");

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
