package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public class MonoCSharpLanguage extends AbstractCompilerLanguage {

    public MonoCSharpLanguage() {
        compiler = "mcs";
        runtime = "mono";
    }

    public MonoCSharpLanguage(Path file) {
        this();
        Path source = normalizeSource(file);
        Path output = createArtifactPath("program.exe");
        configure(
                source,
                "csharp",
                SyntaxConstants.SYNTAX_STYLE_CSHARP,
                output,
                List.of(List.of(compiler, source.toString(), "-out:" + output)),
                List.of(runtime, output.toString())
        );
    }
}
