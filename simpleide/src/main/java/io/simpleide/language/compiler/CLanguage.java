package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class CLanguage extends AbstractCompilerLanguage {

    public CLanguage() {
        compiler = "gcc";
        linker = compiler;
    }

    public CLanguage(Path file) {
        this();
        Path source = normalizeSource(file);
        Path output = createArtifactPath("program.exe");
        configure(
                source,
                "c",
                SyntaxConstants.SYNTAX_STYLE_C,
                output,
                List.of(List.of(compiler, source.toString(), "-o", output.toString())),
                List.of(output.toString())
        );
    }
}
