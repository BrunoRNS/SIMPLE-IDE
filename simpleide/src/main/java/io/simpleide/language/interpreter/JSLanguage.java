package io.simpleide.language.interpreter;

import java.nio.file.Path;
import java.util.List;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class JSLanguage extends AbstractInterpreterLanguage {

  public JSLanguage() {
    interpreter = "node";
  }

  public JSLanguage(Path file) {
    this();
    configure(file, "javascript", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT, interpreter, List.of());
  }
}
