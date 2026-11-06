package io.simpleide.ui.menubar;

import java.awt.Color;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import io.simpleide.ui.editor.Editor;

class ThemeManagerTest {

  private final ThemeManager themeManager = ThemeManager.getInstance();

  @AfterEach
  void resetTheme() throws Exception {
    themeManager.setTheme(ThemeManager.ThemeType.DARK);
    flushEdt();
  }

  @Test
  void setThemeShouldApplyCorrespondingLookAndFeel() throws Exception {
    assertTheme(ThemeManager.ThemeType.LIGHT, FlatLightLaf.class);
    assertTheme(ThemeManager.ThemeType.DRACULA, FlatDarculaLaf.class);
    assertTheme(ThemeManager.ThemeType.DARK, FlatDarkLaf.class);
  }

  @Test
  void setThemeShouldApplyCorrespondingEditorTheme() throws Exception {
    Editor editor = new Editor();
    themeManager.register(editor);

    assertEditorTheme(editor, ThemeManager.ThemeType.LIGHT, Color.WHITE);
    assertEditorTheme(editor, ThemeManager.ThemeType.DARK, new Color(0x293134));
    assertEditorTheme(editor, ThemeManager.ThemeType.DRACULA, new Color(0x282A36));
  }

  private void assertTheme(ThemeManager.ThemeType theme, Class<?> expectedLookAndFeel)
      throws Exception {
    themeManager.setTheme(theme);
    flushEdt();

    assertThat(UIManager.getLookAndFeel().getClass()).isEqualTo(expectedLookAndFeel);
  }

  private void assertEditorTheme(
      Editor editor, ThemeManager.ThemeType theme, Color expectedBackground) throws Exception {
    themeManager.setTheme(theme);
    flushEdt();

    assertThat(editor.getBackground()).isEqualTo(expectedBackground);
  }

  private void flushEdt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
  }
}
