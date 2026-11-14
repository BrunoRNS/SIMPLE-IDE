package io.simpleide.ui.menubar.menus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import java.time.Duration;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThemeMenuTest {

  @AfterEach
  void resetTheme() throws Exception {
    SwingUtilities.invokeAndWait(FlatDarkLaf::setup);
  }

  @Test
  void selectingThemeShouldApplyItAndSelectTheMenuItem() throws Exception {
    ThemeMenu[] themeMenu = new ThemeMenu[1];
    SwingUtilities.invokeAndWait(() -> themeMenu[0] = new ThemeMenu());

    selectThemeAndAssert(themeMenu[0], "Light", FlatLightLaf.class);
    selectThemeAndAssert(themeMenu[0], "Darcula", FlatPropertiesLaf.class);
    selectThemeAndAssert(themeMenu[0], "Brown", FlatPropertiesLaf.class);
    selectThemeAndAssert(themeMenu[0], "Brown Light", FlatPropertiesLaf.class);
    selectThemeAndAssert(themeMenu[0], "Dark", FlatDarkLaf.class);
  }

  private void selectThemeAndAssert(ThemeMenu themeMenu, String theme, Class<?> expectedLookAndFeel)
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          item(themeMenu, theme).doClick();
          assertThat(item(themeMenu, theme).isSelected()).isTrue();
        });

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> assertThat(UIManager.getLookAndFeel().getClass()).isEqualTo(expectedLookAndFeel));
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
