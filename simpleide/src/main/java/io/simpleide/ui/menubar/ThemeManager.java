package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.fife.ui.rsyntaxtextarea.Theme;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.editor.Editor;
import io.simpleide.utils.ResourceUtil;

public final class ThemeManager {

  private static final ThemeManager INSTANCE = new ThemeManager();

  private ThemeType currentTheme = ThemeType.DARK;
  private final List<Editor> editors = new ArrayList<>();

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

    if (!editors.contains(editor)) {
      editors.add(editor);
    }
    ThreadMaster.getInstance().executeOnEDT(() -> applyEditorTheme(editor, currentTheme));
  }

  public void unregister(Editor editor) {
    editors.remove(editor);
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
      for (Editor registeredEditor : editors) {
        if (registeredEditor != null) {
          applyEditorTheme(registeredEditor, theme);
        }
      }
    });
  }

  private void applyEditorTheme(Editor editor, ThemeType theme) {
    String themeResource = switch (theme) {
      case LIGHT -> "default.xml";
      case DARK -> "dark.xml";
      case DRACULA -> "monokai.xml";
    };

    try (InputStream input = Files.newInputStream(
        ResourceUtil.getResourcePath("org.fife.ui.rsyntaxtextarea.themes", themeResource))) {
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
