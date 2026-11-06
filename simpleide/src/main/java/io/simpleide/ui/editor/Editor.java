package io.simpleide.ui.editor;

import java.util.Objects;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;

public class Editor extends RSyntaxTextArea {

  public Editor() {
    super();
    config();
  }

  public void loadFile(File file) {
    Objects.requireNonNull(file, "file");
    setSyntaxEditingStyle(getSyntaxStyle(file.getTypeFile()));
    setText(file.getCode().toString());
    setCaretPosition(0);
  }

  private void config() {
    setCodeFoldingEnabled(true);
    setAutoIndentEnabled(true);
    setAnimateBracketMatching(true);
    setCloseCurlyBraces(true);
    setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
  }

  private String getSyntaxStyle(TypeFile type) {
    return switch (type) {
      case JAVA -> SyntaxConstants.SYNTAX_STYLE_JAVA;
      case GROOVY -> SyntaxConstants.SYNTAX_STYLE_GROOVY;
      case KOTLIN -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
      case JAVASCRIPT -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
      case PYTHON -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
      case C -> SyntaxConstants.SYNTAX_STYLE_C;
      case CPP -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
      case ASM -> SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_6502;
      case TXT, BINARY -> SyntaxConstants.SYNTAX_STYLE_NONE;
    };
  }
}