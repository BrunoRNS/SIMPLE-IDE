package io.simpleide.ui.menubar;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

class ThemeManagerTest {

  private final ThemeManager themeManager = ThemeManager.getInstance();

  @AfterEach
  void resetTheme() throws Exception {
    SwingUtilities.invokeAndWait(() -> themeManager.setTheme(ThemeManager.ThemeType.DARK));
  }

  @Test
  void setThemeShouldApplyCorrespondingLookAndFeel() throws Exception {
    assertTheme(ThemeManager.ThemeType.LIGHT, FlatLightLaf.class);
    assertTheme(ThemeManager.ThemeType.DRACULA, FlatDarculaLaf.class);
    assertTheme(ThemeManager.ThemeType.DARK, FlatDarkLaf.class);
  }

  private void assertTheme(ThemeManager.ThemeType theme, Class<?> expectedLookAndFeel)
      throws Exception {
    SwingUtilities.invokeAndWait(() -> themeManager.setTheme(theme));

    assertThat(UIManager.getLookAndFeel().getClass()).isEqualTo(expectedLookAndFeel);
  }
}
