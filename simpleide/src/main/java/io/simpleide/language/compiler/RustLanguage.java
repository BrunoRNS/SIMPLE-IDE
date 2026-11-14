package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public final class RustLanguage extends AbstractCompilerLanguage {

  public RustLanguage() {
    compiler = "rustc";
  }

  public RustLanguage(Path file) {
    this();
    Path source = normalizeSource(file);
    Path output = createArtifactPath("program.exe");
    configure(
        source,
        "rust",
        SyntaxConstants.SYNTAX_STYLE_RUST,
        output,
        List.of(compilerCommand(source, output)),
        List.of(output.toString()));
  }

  List<String> compilerCommand(Path source, Path output) {
    List<String> command = new ArrayList<>(List.of(compiler));
    String target = windows32Target(System.getProperty("os.name"), System.getProperty("os.arch"));
    if (target != null) {
      command.add("--target=" + target);
      command.add("-C");
      command.add("linker=gcc");
    }
    command.add(source.toString());
    command.add("-o");
    command.add(output.toString());
    return List.copyOf(command);
  }

  static String windows32Target(String osName, String architecture) {
    if (!osName.toLowerCase(Locale.ROOT).contains("win")) {
      return null;
    }
    return switch (architecture.toLowerCase(Locale.ROOT)) {
      case "x86", "i386", "i486", "i586", "i686" -> "i686-pc-windows-gnu";
      default -> null;
    };
  }
}
