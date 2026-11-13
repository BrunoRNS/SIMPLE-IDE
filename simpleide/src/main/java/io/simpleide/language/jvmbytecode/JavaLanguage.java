package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class JavaLanguage extends AbstractBytecodeLanguage {

    public JavaLanguage(Path filePath) {
        super(filePath, "java", SyntaxConstants.SYNTAX_STYLE_JAVA);
    }

}
