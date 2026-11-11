package io.simpleide.language.interpreter;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class Python3Language extends AbstractInterpreterLanguage {

    public Python3Language() {
        interpreter = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
    }

    public Python3Language(Path file) {
        this();
        configure(file, "python3", SyntaxConstants.SYNTAX_STYLE_PYTHON, interpreter, List.of());
    }
}
