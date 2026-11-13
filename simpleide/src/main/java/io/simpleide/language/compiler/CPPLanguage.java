package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class CPPLanguage extends AbstractCompilerLanguage {

    public CPPLanguage() {
        compiler = "g++";
        linker = compiler;
    }

    public CPPLanguage(Path file) {
        this();
        Path source = normalizeSource(file);
        Path output = createArtifactPath("program.exe");
        List<String> command = new ArrayList<>(List.of(compiler, source.toString(), "-o", output.toString()));
        if (isWindows()) {
            command.add("-static-libgcc");
            command.add("-static-libstdc++");
        }
        configure(
                source,
                "c++",
                SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS,
                output,
                List.of(command),
                List.of(output.toString())
        );
    }
}
