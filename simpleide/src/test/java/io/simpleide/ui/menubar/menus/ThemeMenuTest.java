package io.simpleide.ui.menubar.menus;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarculaLaf;

class ThemeMenuTest {

  @AfterEach
  void resetTheme() throws Exception {
    SwingUtilities.invokeAndWait(FlatDarkLaf::setup);
  }

  @Test
  void selectingThemeShouldApplyItAndSelectTheMenuItem() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      ThemeMenu themeMenu = new ThemeMenu();

      item(themeMenu, "Light").doClick();
      assertThat(UIManager.getLookAndFeel().getClass()).isEqualTo(FlatLightLaf.class);
      assertThat(item(themeMenu, "Light").isSelected()).isTrue();

      item(themeMenu, "Darcula").doClick();
      assertThat(UIManager.getLookAndFeel().getClass()).isEqualTo(FlatDarculaLaf.class);
      assertThat(item(themeMenu, "Darcula").isSelected()).isTrue();

      item(themeMenu, "Dark").doClick();
      assertThat(UIManager.getLookAndFeel().getClass()).isEqualTo(FlatDarkLaf.class);
      assertThat(item(themeMenu, "Dark").isSelected()).isTrue();
    });
  }

  private JMenuItem item(ThemeMenu menu, String text) {
    for (int i = 0; i < menu.getItemCount(); i++) {
      JMenuItem item = menu.getItem(i);
      if (item != null && text.equals(item.getText())) {
        return item;
      }
    }
    throw new AssertionError("Menu item not found: " + text);
  }
}
