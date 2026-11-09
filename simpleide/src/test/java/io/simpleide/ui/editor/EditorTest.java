package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.nio.file.Path;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.junit.jupiter.api.Test;

import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;

class EditorTest {

  @Test
  void updatesEditorFontWhenTheUiFontSizeChanges() throws Exception {
    Object originalFont = UIManager.get("TextArea.font");
    try {
      SwingUtilities.invokeAndWait(() -> {
        UIManager.put("TextArea.font", new FontUIResource(Font.MONOSPACED, Font.PLAIN, 15));
        Editor editor = new Editor();
        assertThat(editor.getFont().getSize()).isEqualTo(15);

        UIManager.put("TextArea.font", new FontUIResource(Font.MONOSPACED, Font.PLAIN, 24));
        editor.updateUI();

        assertThat(editor.getFont().getSize()).isEqualTo(24);
      });
    } finally {
      SwingUtilities.invokeAndWait(() -> restoreUiValue("TextArea.font", originalFont));
    }
  }

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

  private static void restoreUiValue(String key, Object value) {
    if (value == null) {
      UIManager.getDefaults().remove(key);
    } else {
      UIManager.put(key, value);
    }
  }
}
