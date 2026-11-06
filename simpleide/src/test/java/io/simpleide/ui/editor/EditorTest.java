package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;

class EditorTest {

  @Test
  void loadFileDisplaysItsContentsAndUsesItsSyntaxStyle() throws Exception {
    CompletableFuture<Void> completed = new CompletableFuture<>();
    ThreadMaster.getInstance().executeOnEDT(() -> {
      try {
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
        completed.complete(null);
      } catch (Throwable failure) {
        completed.completeExceptionally(failure);
      }
    });
    completed.get(2, TimeUnit.SECONDS);
  }
}
