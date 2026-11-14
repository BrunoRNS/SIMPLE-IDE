package io.simpleide.ui.menubar;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.editor.Editor;
import io.simpleide.utils.ResourceUtil;
import java.awt.Color;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

public final class ThemeManager {

  private static final ThemeManager INSTANCE = new ThemeManager();

  private ThemeType currentTheme = ThemeType.DARK;
  private final List<Editor> editors = new ArrayList<>();

  private ThemeManager() {}

  public static ThemeManager getInstance() {
    return INSTANCE;
  }

  public enum ThemeType {
    DARK,
    LIGHT,
    DRACULA,
    BROWN,
    BROWN_LIGHT,
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
    register.executeOnEDT(
        () -> {
          switch (theme) {
            case LIGHT -> FlatLightLaf.setup();
            case DARK -> FlatDarkLaf.setup();
            case DRACULA -> setupPropertiesLookAndFeel("dracula.properties", "Dracula");
            case BROWN -> setupPropertiesLookAndFeel("brown.properties", "Brown");
            case BROWN_LIGHT -> setupPropertiesLookAndFeel("brown-light.properties", "Brown Light");
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
    String themeResource =
        switch (theme) {
          case LIGHT -> "default.xml";
          case DARK -> "dark.xml";
          case DRACULA -> "dracula.xml";
          case BROWN, BROWN_LIGHT -> "dark.xml";
        };

    try {
      Path themePath =
          theme == ThemeType.DRACULA
              ? ResourceUtil.getResourcePath(ThemeManager.class, themeResource)
              : ResourceUtil.getResourcePath("org.fife.ui.rsyntaxtextarea.themes", themeResource);
      try (InputStream input = Files.newInputStream(themePath)) {
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
        if (theme == ThemeType.BROWN || theme == ThemeType.BROWN_LIGHT) {
          boolean lightBrown = theme == ThemeType.BROWN_LIGHT;
          editorTheme.bgColor = new Color(lightBrown ? 0xFBF1C7 : 0x282828);
          editorTheme.caretColor = new Color(lightBrown ? 0x3C3836 : 0xEBDBB2);
          editorTheme.selectionBG = new Color(lightBrown ? 0xD5C4A1 : 0x504945);
          editorTheme.currentLineHighlight = new Color(lightBrown ? 0xF2E5BC : 0x32302F);
          editorTheme.gutterBackgroundColor = editorTheme.bgColor;
          editorTheme.gutterBorderColor = editorTheme.selectionBG;
          editorTheme.lineNumberColor = new Color(lightBrown ? 0xA89984 : 0x928374);
          editorTheme.currentLineNumberColor = editorTheme.caretColor;
        }
        if (theme != ThemeType.DRACULA) {
          applyReadableSyntaxColors(editorTheme, theme);
        }
        editorTheme.apply(editor);
        if (theme == ThemeType.DRACULA) {
          editor.setBracketMatchingEnabled(true);
          editor.setMatchedBracketBGColor(new Color(0x44475A));
          editor.setMatchedBracketBorderColor(new Color(0xFF79C6));
          editor.setPaintMatchedBracketPair(true);
          editor.setMarkOccurrencesColor(new Color(0x44475A));
          editor.setMarkAllHighlightColor(new Color(0x44475A));
          editor.setHyperlinkForeground(new Color(0x8BE9FD));
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load editor theme: " + themeResource, exception);
    }
  }

  private void setupPropertiesLookAndFeel(String resourceName, String themeName) {
    try (InputStream input = ThemeManager.class.getResourceAsStream(resourceName)) {
      if (input == null) {
        throw new IllegalStateException(
            "Unable to load " + themeName + " look and feel properties");
      }
      UIManager.setLookAndFeel(new FlatPropertiesLaf(themeName, input));
    } catch (IOException | UnsupportedLookAndFeelException exception) {
      throw new IllegalStateException("Unable to apply " + themeName + " look and feel", exception);
    }
  }

  private void applyReadableSyntaxColors(Theme editorTheme, ThemeType theme) {
    Color comments;
    Color keywords;
    Color types;
    Color functions;
    Color strings;
    Color numbers;
    Color punctuation;
    Color commentKeywords;
    Color identifiers;
    Color variables;
    Color annotations;
    Color regexes;
    Color booleans;
    Color errors;
    Color markupTags;
    Color markupAttributes;
    Color markupValues;
    Color markupOther;

    if (theme == ThemeType.LIGHT) {
      comments = new Color(0x687A64);
      keywords = new Color(0xA13D63);
      types = new Color(0x246C72);
      functions = new Color(0x315FA3);
      strings = new Color(0x8A5A00);
      numbers = new Color(0x7952A3);
      punctuation = new Color(0x81511D);
      commentKeywords = new Color(0x8A5A00);
      identifiers = new Color(0x315FA3);
      variables = new Color(0x246C72);
      annotations = new Color(0xA13D63);
      regexes = new Color(0x7952A3);
      booleans = new Color(0x7952A3);
      errors = new Color(0xB00020);
      markupTags = new Color(0xA13D63);
      markupAttributes = new Color(0x246C72);
      markupValues = new Color(0x8A5A00);
      markupOther = new Color(0x7952A3);
    } else if (theme == ThemeType.BROWN) {
      comments = new Color(0x928374);
      keywords = new Color(0xFB4934);
      types = new Color(0xFABD2F);
      functions = new Color(0xB8BB26);
      strings = new Color(0xB8BB26);
      numbers = new Color(0xD3869B);
      punctuation = new Color(0xEBDBB2);
      commentKeywords = new Color(0xFE8019);
      identifiers = new Color(0x83A598);
      variables = new Color(0x8EC07C);
      annotations = new Color(0xFE8019);
      regexes = new Color(0x83A598);
      booleans = new Color(0xD3869B);
      errors = new Color(0xFB4934);
      markupTags = new Color(0xFB4934);
      markupAttributes = new Color(0xFABD2F);
      markupValues = new Color(0xB8BB26);
      markupOther = new Color(0x83A598);
    } else if (theme == ThemeType.BROWN_LIGHT) {
      comments = new Color(0xA89984);
      keywords = new Color(0x9D0006);
      types = new Color(0xB57614);
      functions = new Color(0x79740E);
      strings = new Color(0x79740E);
      numbers = new Color(0x8F3F71);
      punctuation = new Color(0x3C3836);
      commentKeywords = new Color(0xAF3A03);
      identifiers = new Color(0x076678);
      variables = new Color(0x427B58);
      annotations = new Color(0xAF3A03);
      regexes = new Color(0x076678);
      booleans = new Color(0x8F3F71);
      errors = new Color(0x9D0006);
      markupTags = new Color(0x9D0006);
      markupAttributes = new Color(0xB57614);
      markupValues = new Color(0x79740E);
      markupOther = new Color(0x076678);
    } else {
      comments = new Color(0x8FA39A);
      keywords = new Color(0xF08A9B);
      types = new Color(0x9CCB8A);
      functions = new Color(0x83C5E8);
      strings = new Color(0xE4C07A);
      numbers = new Color(0xC6A0F6);
      punctuation = new Color(0xD5DCE5);
      commentKeywords = new Color(0xE4C07A);
      identifiers = new Color(0xD5DCE5);
      variables = new Color(0x83C5E8);
      annotations = new Color(0xF08A9B);
      regexes = new Color(0xE4C07A);
      booleans = new Color(0xC6A0F6);
      errors = new Color(0xF08A9B);
      markupTags = new Color(0xF08A9B);
      markupAttributes = new Color(0x9CCB8A);
      markupValues = new Color(0xE4C07A);
      markupOther = new Color(0x83C5E8);
    }

    setForeground(
        editorTheme,
        comments,
        TokenTypes.COMMENT_EOL,
        TokenTypes.COMMENT_MULTILINE,
        TokenTypes.COMMENT_DOCUMENTATION);
    setForeground(
        editorTheme, commentKeywords, TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP);
    setForeground(editorTheme, keywords, TokenTypes.RESERVED_WORD, TokenTypes.RESERVED_WORD_2);
    setForeground(editorTheme, types, TokenTypes.DATA_TYPE);
    setForeground(editorTheme, functions, TokenTypes.FUNCTION);
    setForeground(editorTheme, identifiers, TokenTypes.IDENTIFIER);
    setForeground(editorTheme, variables, TokenTypes.VARIABLE);
    setForeground(
        editorTheme,
        strings,
        TokenTypes.LITERAL_STRING_DOUBLE_QUOTE,
        TokenTypes.LITERAL_CHAR,
        TokenTypes.LITERAL_BACKQUOTE);
    setForeground(
        editorTheme,
        numbers,
        TokenTypes.LITERAL_NUMBER_DECIMAL_INT,
        TokenTypes.LITERAL_NUMBER_FLOAT,
        TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
    setForeground(editorTheme, booleans, TokenTypes.LITERAL_BOOLEAN);
    setForeground(editorTheme, annotations, TokenTypes.ANNOTATION);
    setForeground(editorTheme, regexes, TokenTypes.REGEX);
    setForeground(
        editorTheme,
        errors,
        TokenTypes.ERROR_IDENTIFIER,
        TokenTypes.ERROR_NUMBER_FORMAT,
        TokenTypes.ERROR_STRING_DOUBLE,
        TokenTypes.ERROR_CHAR);
    setForeground(
        editorTheme,
        markupTags,
        TokenTypes.MARKUP_TAG_DELIMITER,
        TokenTypes.MARKUP_TAG_NAME,
        TokenTypes.MARKUP_PROCESSING_INSTRUCTION,
        TokenTypes.MARKUP_CDATA_DELIMITER);
    setForeground(editorTheme, markupAttributes, TokenTypes.MARKUP_TAG_ATTRIBUTE);
    setForeground(editorTheme, markupValues, TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE);
    setForeground(
        editorTheme,
        markupOther,
        TokenTypes.MARKUP_DTD,
        TokenTypes.MARKUP_ENTITY_REFERENCE,
        TokenTypes.MARKUP_CDATA);
    setForeground(editorTheme, comments, TokenTypes.MARKUP_COMMENT);
    setForeground(
        editorTheme,
        punctuation,
        TokenTypes.OPERATOR,
        TokenTypes.SEPARATOR,
        TokenTypes.PREPROCESSOR);
    applyDistinctSyntaxColors(editorTheme, theme);
  }

  private void applyDistinctSyntaxColors(Theme editorTheme, ThemeType theme) {
    if (theme == ThemeType.BROWN) {
      setForeground(editorTheme, new Color(0xA89984), TokenTypes.COMMENT_MULTILINE);
      setForeground(editorTheme, new Color(0xBDAE93), TokenTypes.COMMENT_DOCUMENTATION);
      setForeground(editorTheme, new Color(0xFE8019), TokenTypes.COMMENT_MARKUP);
      setForeground(editorTheme, new Color(0xD3869B), TokenTypes.RESERVED_WORD_2);
      setForeground(editorTheme, new Color(0xE0A577), TokenTypes.LITERAL_NUMBER_FLOAT);
      setForeground(editorTheme, new Color(0xD3869B), TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
      setForeground(editorTheme, new Color(0xFE8019), TokenTypes.LITERAL_CHAR);
      setForeground(editorTheme, new Color(0x83A598), TokenTypes.LITERAL_BACKQUOTE);
      setForeground(editorTheme, new Color(0xFE8019), TokenTypes.ERROR_NUMBER_FORMAT);
      setForeground(editorTheme, new Color(0xFB4934), TokenTypes.ERROR_STRING_DOUBLE);
      setForeground(editorTheme, new Color(0xFF6B5E), TokenTypes.ERROR_CHAR);
      setForeground(editorTheme, new Color(0xE4685D), TokenTypes.MARKUP_TAG_NAME);
      setForeground(editorTheme, new Color(0xD3869B), TokenTypes.MARKUP_PROCESSING_INSTRUCTION);
      setForeground(editorTheme, new Color(0x83A598), TokenTypes.MARKUP_CDATA_DELIMITER);
      setForeground(editorTheme, new Color(0xD3869B), TokenTypes.MARKUP_DTD);
      setForeground(editorTheme, new Color(0x8EC07C), TokenTypes.MARKUP_ENTITY_REFERENCE);
      setForeground(editorTheme, new Color(0x83A598), TokenTypes.MARKUP_CDATA);
    } else if (theme == ThemeType.BROWN_LIGHT) {
      setForeground(editorTheme, new Color(0x928374), TokenTypes.COMMENT_MULTILINE);
      setForeground(editorTheme, new Color(0x7C6F64), TokenTypes.COMMENT_DOCUMENTATION);
      setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.COMMENT_MARKUP);
      setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.RESERVED_WORD_2);
      setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.LITERAL_NUMBER_FLOAT);
      setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
      setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.LITERAL_CHAR);
      setForeground(editorTheme, new Color(0x076678), TokenTypes.LITERAL_BACKQUOTE);
      setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.ERROR_NUMBER_FORMAT);
      setForeground(editorTheme, new Color(0x9D0006), TokenTypes.ERROR_STRING_DOUBLE);
      setForeground(editorTheme, new Color(0xB00020), TokenTypes.ERROR_CHAR);
      setForeground(editorTheme, new Color(0xA31218), TokenTypes.MARKUP_TAG_NAME);
      setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.MARKUP_PROCESSING_INSTRUCTION);
      setForeground(editorTheme, new Color(0x076678), TokenTypes.MARKUP_CDATA_DELIMITER);
      setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.MARKUP_DTD);
      setForeground(editorTheme, new Color(0x427B58), TokenTypes.MARKUP_ENTITY_REFERENCE);
      setForeground(editorTheme, new Color(0x076678), TokenTypes.MARKUP_CDATA);
    }
  }

  private void setForeground(Theme theme, Color color, int... tokenTypes) {
    for (int tokenType : tokenTypes) {
      theme.scheme.getStyle(tokenType).foreground = color;
    }
  }
}
