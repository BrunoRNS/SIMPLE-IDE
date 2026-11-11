package io.simpleide.language.compiler;

import java.nio.file.Files;
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
                runtimeCommand(output)
        );
    }

    List<String> compilerCommand(Path source, Path output) {
        Path runtimeLauncher = windowsRuntimeLauncher();
        if (runtimeLauncher != null) {
            Path monoHome = runtimeLauncher.getParent().getParent();
            Path managedCompiler = monoHome.resolve("lib").resolve("mono").resolve("4.5").resolve("mcs.exe");
            if (Files.isRegularFile(managedCompiler)) {
                return List.of(
                        runtimeLauncher.toString(),
                        managedCompiler.toString(),
                        source.toString(),
                        "-out:" + output
                );
            }
        }
        return List.of(compiler, source.toString(), "-out:" + output);
    }

    private List<String> runtimeCommand(Path assembly) {
        Path runtimeLauncher = windowsRuntimeLauncher();
        return List.of(
                runtimeLauncher == null ? runtime : runtimeLauncher.toString(),
                assembly.toString()
        );
    }

    private Path windowsRuntimeLauncher() {
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }
        Path runtimeLauncher = OSpathUtil.which(runtime);
        return runtimeLauncher != null && !isBatchFile(runtimeLauncher) ? runtimeLauncher : null;
    }

    private boolean isBatchFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".bat") || fileName.endsWith(".cmd");
    }
}
