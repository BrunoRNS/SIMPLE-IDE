package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;

class EditorTest {

  @TempDir
  Path tempDirectory;

  @Test
  void loadFileDisplaysItsContentsAndUsesItsSyntaxStyle() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Editor editor = new Editor();
      File pythonFile = new File(
          Path.of("main.py"), "main.py", TypeFile.PYTHON, new StringBuffer("print('hello')"));

      editor.loadFile(pythonFile);

      assertThat(editor.getText()).isEqualTo("print('hello')");
      assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_PYTHON);

      File textFile = new File(
          Path.of("notes.txt"), "notes.txt", TypeFile.TXT, new StringBuffer("plain text"));
      editor.loadFile(textFile);

      assertThat(editor.getText()).isEqualTo("plain text");
      assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_NONE);
    });
  }

  @Test
  void loadUnsupportedAssemblyAsPlainText() throws Exception {
    Path assemblyPath = Files.writeString(tempDirectory.resolve("main.asm"), "lda #$00");
    File assemblyFile = new File(assemblyPath);

    SwingUtilities.invokeAndWait(() -> {
      Editor editor = new Editor();

      editor.loadFile(assemblyFile);

      assertThat(assemblyFile.getTypeFile()).isEqualTo(TypeFile.TXT);
      assertThat(editor.getText()).isEqualTo("lda #$00");
      assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_NONE);
    });
  }
}
