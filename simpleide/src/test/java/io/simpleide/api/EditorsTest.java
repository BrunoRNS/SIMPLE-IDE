package io.simpleide.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.simpleide.ui.editor.Editor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorsTest {

  @TempDir Path tempDirectory;

  @Test
  void createReturnsAnEmptyEditor() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          assertThat(Editors.create().getText()).isEmpty();
          assertThat(Editors.create().getText()).isNotNull();
        });
  }

  @Test
  void createWithContentPreLoadsTheText() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> assertThat(Editors.create("hello").getText()).isEqualTo("hello"));
  }

  @Test
  void createRejectsNullContent() {
    assertThatNullPointerException().isThrownBy(() -> Editors.create(null));
  }

  @Test
  void openLoadsTheFileAndSelectsItsSyntaxStyle() throws Exception {
    Path source = Files.writeString(tempDirectory.resolve("Main.java"), "class Main {}");
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            Editor editor = Editors.open(source);
            assertThat(editor.getText()).isEqualTo("class Main {}");
            assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_JAVA);
          } catch (IOException error) {
            throw new AssertionError(error);
          }
        });
  }

  @Test
  void openRejectsNullAndMissingFiles() {
    assertThatNullPointerException().isThrownBy(() -> Editors.open(null));
    assertThatIOException().isThrownBy(() -> Editors.open(tempDirectory.resolve("missing.java")));
  }

  @Test
  void scrollPaneWrapsTheEditor() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          RTextScrollPane pane = Editors.scrollPane(Editors.create());
          assertThat((JScrollPane) pane).isInstanceOf(RTextScrollPane.class);
        });
  }

  @Test
  void scrollPaneRejectsNull() {
    assertThatNullPointerException().isThrownBy(() -> Editors.scrollPane(null));
  }

  @Test
  void syntaxStyleForResolvesKnownExtensions() {
    assertThat(Editors.syntaxStyleFor(tempDirectory.resolve("main.kt")))
        .isEqualTo(SyntaxConstants.SYNTAX_STYLE_KOTLIN);
    assertThat(Editors.syntaxStyleFor(tempDirectory.resolve("notes.unknown")))
        .isEqualTo(SyntaxConstants.SYNTAX_STYLE_NONE);
  }

  @Test
  void applySyntaxStyleUpdatesTheEditor() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          Editor editor = Editors.create();
          Editors.applySyntaxStyle(editor, tempDirectory.resolve("script.py"));
          assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_PYTHON);
          Editors.applySyntaxStyle(editor, io.simpleide.ui.treefile.TypeFile.JAVA);
          assertThat(editor.getSyntaxEditingStyle()).isEqualTo(SyntaxConstants.SYNTAX_STYLE_JAVA);
        });
  }

  @Test
  void textAreaTypeIsTheConcreteEditorClass() {
    assertThat(Editors.textAreaType()).isEqualTo(Editor.class);
  }
}
