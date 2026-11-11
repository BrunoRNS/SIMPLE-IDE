package io.simpleide.ui.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    AtomicReference<EditorTab> tabReference = new AtomicReference<>();

    SwingUtilities.invokeAndWait(() -> {
      try {
        EditorTab tab = new EditorTab(new File(path), new Document(path, "java"));
        tab.getEditor().setText("class Main { String text; }");
        tabReference.set(tab);
      } catch (IOException error) {
        throw new AssertionError(error);
      }
    });

    await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
      SwingUtilities.invokeAndWait(() -> {
        assertThat(tabReference.get().isEdit()).isTrue();
        assertThat(tabReference.get().getDocument().isModified()).isTrue();
      }));

    SwingUtilities.invokeAndWait(() -> {
      try {
        tabReference.get().save();

        assertThat(tabReference.get().isEdit()).isFalse();
        assertThat(tabReference.get().getDocument().isModified()).isFalse();
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
