package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.fife.ui.rsyntaxtextarea.Theme;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.editor.Editor;

public final class ThemeManager {

  private static final ThemeManager INSTANCE = new ThemeManager();

  private ThemeType currentTheme = ThemeType.DARK;
  private Editor editor;

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

  public void register(Editor editor) {
    if (editor == null) {
      return;
    }

    this.editor = editor;
    ThreadMaster.getInstance().executeOnEDT(() -> applyEditorTheme(editor, currentTheme));
  }

  public void setTheme(ThemeType newTheme) {
    this.currentTheme = newTheme;
    applyTheme(newTheme);
  }

  private void applyTheme(ThemeType theme) {
    ThreadMaster register = ThreadMaster.getInstance();
    register.executeOnEDT(() -> {
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
        window.validate();
        window.repaint();
      }
      if (editor != null) {
        applyEditorTheme(editor, theme);
      }
    });
  }

  private void applyEditorTheme(Editor editor, ThemeType theme) {
    String themeResource = switch (theme) {
      case LIGHT -> "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
      case DARK -> "/org/fife/ui/rsyntaxtextarea/themes/dark.xml";
      case DRACULA -> "/org/fife/ui/rsyntaxtextarea/themes/monokai.xml";
    };

    try (InputStream input = ThemeManager.class.getResourceAsStream(themeResource)) {
      if (input == null) {
        throw new IllegalStateException("Editor theme resource not found: " + themeResource);
      }

      Theme editorTheme = Theme.load(input);
      if (theme == ThemeType.DRACULA) {
        editorTheme.bgColor = new Color(0x282A36);
        editorTheme.caretColor = new Color(0xF8F8F2);
        editorTheme.selectionBG = new Color(0x44475A);
        editorTheme.currentLineHighlight = new Color(0x44475A);
        editorTheme.gutterBackgroundColor = new Color(0x282A36);
        editorTheme.gutterBorderColor = new Color(0x44475A);
        editorTheme.lineNumberColor = new Color(0x6272A4);
        editorTheme.currentLineNumberColor = new Color(0xF8F8F2);
      }
      editorTheme.apply(editor);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load editor theme: " + themeResource, exception);
    }
  }
}
