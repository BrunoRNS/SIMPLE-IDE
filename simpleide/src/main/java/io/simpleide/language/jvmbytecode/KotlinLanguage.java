package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class KotlinLanguage extends AbstractBytecodeLanguage {

  public KotlinLanguage(Path filePath) {
    super(filePath, "kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
  }
}
