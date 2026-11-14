package io.simpleide.ui.editor;

import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;
import java.awt.Font;
import java.util.Objects;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public class Editor extends RSyntaxTextArea {

  public Editor() {
    super();
    applyUiFont();
    config();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    applyUiFont();
  }

  private void applyUiFont() {
    Font editorFont = UIManager.getFont("TextArea.font");
    if (editorFont != null) {
      setFont(editorFont);
    }
  }

  public void loadFile(File file) {
    Objects.requireNonNull(file, "file");
    setSyntaxStyle(file.getTypeFile());
    setText(file.getCode().toString());
    setCaretPosition(0);
  }

  /**
   * Applies the editor syntax style that corresponds to a file type.
   *
   * @param type file type to apply
   */
  public void setSyntaxStyle(TypeFile type) {
    setSyntaxEditingStyle(syntaxStyleFor(type));
  }

  private void config() {
    setCodeFoldingEnabled(true);
    setAutoIndentEnabled(true);
    setAnimateBracketMatching(true);
    setCloseCurlyBraces(true);
    setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
  }

  /**
   * Returns the RSyntaxTextArea syntax style constant for a file type.
   *
   * @param type file type to translate
   * @return an RSyntaxTextArea syntax style constant
   */
  public static String syntaxStyleFor(TypeFile type) {
    Objects.requireNonNull(type, "type");
    return switch (type) {
      case JAVA -> SyntaxConstants.SYNTAX_STYLE_JAVA;
      case CSHARP -> SyntaxConstants.SYNTAX_STYLE_CSHARP;
      case GROOVY -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
      case KOTLIN -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
      case JAVASCRIPT -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
      case GO -> SyntaxConstants.SYNTAX_STYLE_GO;
      case RUST -> SyntaxConstants.SYNTAX_STYLE_RUST;
      case PYTHON -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
      case C -> SyntaxConstants.SYNTAX_STYLE_C;
      case CPP -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
      case TXT, BINARY -> SyntaxConstants.SYNTAX_STYLE_NONE;
    };
  }
}
