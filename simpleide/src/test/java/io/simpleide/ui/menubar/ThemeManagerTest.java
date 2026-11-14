package io.simpleide.ui.menubar;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.ui.editor.Editor;
import java.awt.Color;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
    assertTheme(ThemeManager.ThemeType.DRACULA, FlatPropertiesLaf.class);
    assertTheme(ThemeManager.ThemeType.BROWN, FlatPropertiesLaf.class);
    assertTheme(ThemeManager.ThemeType.BROWN_LIGHT, FlatPropertiesLaf.class);
    assertTheme(ThemeManager.ThemeType.DARK, FlatDarkLaf.class);
  }

  @Test
  void setThemeShouldApplyCorrespondingEditorTheme() throws Exception {
    Editor editor = new Editor();
    themeManager.register(editor);

    assertEditorTheme(editor, ThemeManager.ThemeType.LIGHT, Color.WHITE);
    assertEditorTheme(editor, ThemeManager.ThemeType.DARK, new Color(0x293134));
    assertEditorTheme(editor, ThemeManager.ThemeType.DRACULA, new Color(0x282A36));
    assertEditorTheme(editor, ThemeManager.ThemeType.BROWN, new Color(0x282828));
    assertEditorTheme(editor, ThemeManager.ThemeType.BROWN_LIGHT, new Color(0xFBF1C7));
  }

  @Test
  void draculaShouldUseVisualStudioCodePaletteAcrossEditorAndUi() throws Exception {
    Editor editor = new Editor();
    themeManager.register(editor);
    themeManager.setTheme(ThemeManager.ThemeType.DRACULA);
    flushEdt();

    assertThat(editor.getBackground()).isEqualTo(new Color(0x282A36));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.COMMENT_EOL).foreground)
        .isEqualTo(new Color(0x6272A4));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
        .isEqualTo(new Color(0xFF79C6));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.DATA_TYPE).foreground)
        .isEqualTo(new Color(0x8BE9FD));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.IDENTIFIER).foreground)
        .isEqualTo(new Color(0x8BE9FD));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.FUNCTION).foreground)
        .isEqualTo(new Color(0x50FA7B));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD_2).foreground)
        .isEqualTo(new Color(0xBD93F9));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.COMMENT_MULTILINE).foreground)
        .isNotEqualTo(editor.getSyntaxScheme().getStyle(TokenTypes.COMMENT_EOL).foreground);
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_FLOAT).foreground)
        .isNotEqualTo(
            editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).foreground);
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.MARKUP_TAG_NAME).foreground)
        .isEqualTo(new Color(0xFF6B9E));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.ERROR_CHAR).foreground)
        .isEqualTo(new Color(0xFF9B9B));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.VARIABLE).foreground)
        .isEqualTo(new Color(0xFFB86C));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground)
        .isEqualTo(new Color(0xF1FA8C));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).foreground)
        .isEqualTo(new Color(0xBD93F9));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.SEPARATOR).foreground)
        .isEqualTo(new Color(0xFF79C6));
    assertThat(editor.getMatchedBracketBGColor()).isEqualTo(new Color(0x44475A));
    assertThat(editor.getMatchedBracketBorderColor()).isEqualTo(new Color(0xFF79C6));
    assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x282A36));
    assertThat(UIManager.getColor("MenuBar.hoverBackground")).isEqualTo(new Color(0x44475A));
    assertThat(UIManager.getColor("Button.background")).isEqualTo(new Color(0x44475A));
  }

  @Test
  void brownThemesShouldUseDistinctReadableSyntaxColors() throws Exception {
    Editor editor = new Editor();
    themeManager.register(editor);
    themeManager.setTheme(ThemeManager.ThemeType.BROWN);
    flushEdt();

    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
        .isEqualTo(new Color(0xFB4934));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.FUNCTION).foreground)
        .isEqualTo(new Color(0xB8BB26));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.IDENTIFIER).foreground)
        .isEqualTo(new Color(0x83A598));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.ANNOTATION).foreground)
        .isEqualTo(new Color(0xFE8019));
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground)
        .isEqualTo(new Color(0xB8BB26));
    assertThat(UIManager.getColor("Tree.background")).isEqualTo(new Color(0x282828));
    assertThat(UIManager.getColor("MenuBar.hoverBackground")).isEqualTo(new Color(0x504945));
    assertThat(UIManager.getColor("TextPane.background")).isEqualTo(new Color(0x282828));
    assertThat(UIManager.getColor("MenuBar.background")).isEqualTo(new Color(0x282828));
    assertThat(UIManager.getColor("PopupMenu.background")).isEqualTo(new Color(0x282828));
    assertThat(UIManager.getColor("OptionPane.background")).isEqualTo(new Color(0x282828));
    assertThat(UIManager.getColor("Button.background")).isEqualTo(new Color(0x3C3836));
    LogPanel logPanel = new LogPanel();
    SwingUtilities.updateComponentTreeUI(logPanel);
    assertThat(logPanel.getBackground()).isEqualTo(new Color(0x282828));

    themeManager.setTheme(ThemeManager.ThemeType.BROWN_LIGHT);
    flushEdt();
    assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
        .isEqualTo(new Color(0x9D0006));
    assertThat(UIManager.getColor("Tree.background")).isEqualTo(new Color(0xFBF1C7));
    assertThat(UIManager.getColor("MenuBar.hoverBackground")).isEqualTo(new Color(0xD5C4A1));
    assertThat(UIManager.getColor("TextPane.background")).isEqualTo(new Color(0xFBF1C7));
    assertThat(UIManager.getColor("MenuBar.background")).isEqualTo(new Color(0xFBF1C7));
    assertThat(UIManager.getColor("PopupMenu.background")).isEqualTo(new Color(0xFBF1C7));
    assertThat(UIManager.getColor("OptionPane.background")).isEqualTo(new Color(0xFBF1C7));
    assertThat(UIManager.getColor("Button.background")).isEqualTo(new Color(0xEBDBB2));
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
