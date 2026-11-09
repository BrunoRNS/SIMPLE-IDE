package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.core.Document;
import io.simpleide.ui.treefile.File;

class EditorTabTest {

  @TempDir
  Path root;

  @Test
  void savesEditorTextAndClearsModifiedState() throws Exception {
    Path path = Files.writeString(root.resolve("Main.java"), "class Main {}");

    SwingUtilities.invokeAndWait(() -> {
      try {
        EditorTab tab = new EditorTab(new File(path), new Document(path, "java"));
        tab.getEditor().setText("class Main { String text; }");

        assertThat(tab.isEdit()).isTrue();
        assertThat(tab.getDocument().isModified()).isTrue();

        tab.save();

        assertThat(tab.isEdit()).isFalse();
        assertThat(tab.getDocument().isModified()).isFalse();
      } catch (IOException error) {
        throw new AssertionError(error);
      }
    });

    assertThat(Files.readString(path)).isEqualTo("class Main { String text; }");
  }

  @Test
  void selectsTheTargetRangeForDefinitionNavigation() throws Exception {
    Path path = Files.writeString(root.resolve("Main.java"), "first\nsecond line");

    SwingUtilities.invokeAndWait(() -> {
      try {
        EditorTab tab = new EditorTab(new File(path), new Document(path, "java"));

        tab.goToRange(new Range(new Position(1, 1), new Position(1, 6)));

        assertThat(tab.getEditor().getSelectedText()).isEqualTo("econd");
      } catch (IOException error) {
        throw new AssertionError(error);
      }
    });
  }
}
