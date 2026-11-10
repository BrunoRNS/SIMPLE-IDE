package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import io.simpleide.utils.OSpathUtil;

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
                List.of(compilerCommand(source, output)),
                List.of(runtime, output.toString())
        );
    }

    List<String> compilerCommand(Path source, Path output) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            Path compilerLauncher = OSpathUtil.which(compiler);
            if (compilerLauncher != null && isBatchFile(compilerLauncher)) {
                Path monoHome = compilerLauncher.getParent().getParent();
                Path managedCompiler = monoHome.resolve("lib").resolve("mono").resolve("4.5").resolve("mcs.exe");
                return List.of(runtime, managedCompiler.toString(), source.toString(), "-out:" + output);
            }
        }
        return List.of(compiler, source.toString(), "-out:" + output);
    }

    private boolean isBatchFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".bat") || fileName.endsWith(".cmd");
    }
}
