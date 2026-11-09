package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public class CLanguage extends AbstractCompilerLanguage {

    public CLanguage() {
        compiler = "gcc";
        assembler = "as";
        linker = compiler;
    }

    public CLanguage(Path file) {
        this();
        Path source = normalizeSource(file);
        Path assembly = createArtifactPath("program.s");
        Path object = createArtifactPath("program.o");
        Path output = createArtifactPath("program.exe");
        configure(
                source,
                "c",
                SyntaxConstants.SYNTAX_STYLE_C,
                output,
                List.of(
                        List.of(compiler, "-S", source.toString(), "-o", assembly.toString()),
                        List.of(assembler, assembly.toString(), "-o", object.toString()),
                        List.of(linker, object.toString(), "-o", output.toString())
                ),
                List.of(output.toString())
        );
    }
}
