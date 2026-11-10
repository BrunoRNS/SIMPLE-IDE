package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public class CPPLanguage extends AbstractCompilerLanguage {

    public CPPLanguage() {
        compiler = "g++";
        assembler = "as";
        linker = compiler;
    }

    public CPPLanguage(Path file) {
        this();
        Path source = normalizeSource(file);
        Path assembly = createArtifactPath("program.s");
        Path object = createArtifactPath("program.o");
        Path output = createArtifactPath("program.exe");
        configure(
                source,
                "c++",
                SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS,
                output,
                List.of(
                        List.of(compiler, "-S", source.toString(), "-o", assembly.toString()),
                        List.of(assembler, assembly.toString(), "-o", object.toString()),
                        createLinkCommand(object, output)
                ),
                List.of(output.toString())
        );
    }
}
