package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import io.simpleide.utils.OSpathUtil;

public class Assembly65xLanguage extends AbstractCompilerLanguage {

    public Assembly65xLanguage() {
        assembler = "wla65816";
        linker = "wlalink";
        runtime = "Mesen-S";
    }

    public Assembly65xLanguage(Path filePath) {
        this();
        Path source = normalizeSource(filePath);
        Path output = createArtifactPath("program.sfc");
        Path object = createArtifactPath("program.o");
        Path linkFile = createArtifactPath("program.link");
        writeArtifact(linkFile, "[objects]\n" + object.getFileName() + "\n");
        String assemblerCommand = findAssemblerCommand();
        configure(
                source,
                "6502 assembly",
                SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86,
                output,
                List.of(
                        List.of(assemblerCommand, "-o", object.toString(), source.toString()),
                        List.of(linker, "-v", linkFile.toString(), output.toString())
                ),
                List.of(runtime, output.toString())
        );
        processSteps.get(1).directory(getArtifactDirectory().toFile());
    }

    public Assembly65xLanguage(Path filePath, String friendlyName, String languageConstant) {
        this(filePath);
        langFriendly = friendlyName;
        langConstant = languageConstant;
    }

    private static String findAssemblerCommand() {
        if (OSpathUtil.which("wla65816") != null) {
            return "wla65816";
        }
        return OSpathUtil.which("wla-65816") == null ? "wla65816" : "wla-65816";
    }
}
