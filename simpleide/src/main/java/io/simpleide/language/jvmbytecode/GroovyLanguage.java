package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class GroovyLanguage extends AbstractBytecodeLanguage {

  public GroovyLanguage(Path filePath) {
    super(filePath, "groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY);
  }
}
