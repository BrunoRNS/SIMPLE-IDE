package io.simpleide.frame.menubar;

import java.awt.Window;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

public final class ThemeManager {

  private static final ThemeManager INSTANCE = new ThemeManager();

  private ThemeType currentTheme = ThemeType.DARK;

  private ThemeManager() {
  }

  public static ThemeManager getInstance() {
    return INSTANCE;
  }

  public enum ThemeType {
    DARK,
    LIGHT,
    DRACULA,
  }

  public void register(Window window) {
    if (window == null) {
      return;
    }

    applyTheme(currentTheme);
  }

  public void setTheme(ThemeType newTheme) {
    this.currentTheme = newTheme;
    applyTheme(newTheme);
  }

  private void applyTheme(ThemeType theme) {
    switch (theme) {
      case LIGHT -> FlatLightLaf.setup();
      case DARK -> FlatDarkLaf.setup();
      case DRACULA -> FlatDarculaLaf.setup();
    }
    for (Window window : Window.getWindows()) {
      if (window == null) {
        continue;
      }
      SwingUtilities.updateComponentTreeUI(window);
      window.pack();
      window.repaint();
    }
  }
}
