package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class GoLanguage extends AbstractCompilerLanguage {

    public GoLanguage() {
        compiler = "go";
    }

    public GoLanguage(Path file) {
        this();
        Path source = normalizeSource(file);
        Path output = createArtifactPath("program.exe");
        configure(
                source,
                "go",
                SyntaxConstants.SYNTAX_STYLE_GO,
                output,
                List.of(List.of(compiler, "build", "-o", output.toString(), source.toString())),
                List.of(output.toString())
        );
    }
}
