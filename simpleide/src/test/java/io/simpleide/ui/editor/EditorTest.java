package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.ui.treefile.File;

class EditorTest {

  @TempDir
  Path tempDirectory;

  @Test
  void loadFileDisplaysItsContentsAndUsesSyntaxStylesForEveryLanguage() throws Exception {
    String[] extensions = {
        "java", "kt", "groovy", "js", "py", "cs", "cpp", "c", "go", "rs"
    };
    String[] syntaxStyles = {
        SyntaxConstants.SYNTAX_STYLE_JAVA, SyntaxConstants.SYNTAX_STYLE_KOTLIN,
        SyntaxConstants.SYNTAX_STYLE_GROOVY, SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT,
        SyntaxConstants.SYNTAX_STYLE_PYTHON, SyntaxConstants.SYNTAX_STYLE_CSHARP,
        SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS, SyntaxConstants.SYNTAX_STYLE_C,
        SyntaxConstants.SYNTAX_STYLE_GO, SyntaxConstants.SYNTAX_STYLE_RUST
    };
    File[] files = new File[extensions.length];
    for (int index = 0; index < extensions.length; index++) {
      files[index] = new File(Files.writeString(
          tempDirectory.resolve("main." + extensions[index]), "source"));
    }
    File textFile = new File(Files.writeString(tempDirectory.resolve("notes.txt"), "plain text"));

    SwingUtilities.invokeAndWait(() -> {
      Editor editor = new Editor();
      for (int index = 0; index < files.length; index++) {
        editor.loadFile(files[index]);
        assertThat(editor.getSyntaxEditingStyle()).isEqualTo(syntaxStyles[index]);
      }
      editor.loadFile(textFile);

      assertThat(editor.getText()).isEqualTo("plain text");
      assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_NONE);
    });
  }
}
