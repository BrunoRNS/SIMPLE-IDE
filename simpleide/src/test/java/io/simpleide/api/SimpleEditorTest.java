package io.simpleide.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimpleEditorTest {

  @TempDir Path tempDirectory;

  @Test
  void openLoadsTheFileAndExposesItsParts() throws Exception {
    Path source = Files.writeString(tempDirectory.resolve("Main.java"), "class Main {}");
    SwingUtilities.invokeAndWait(
        () -> {
          try (SimpleEditor editor = SimpleEditor.open(source)) {
            assertThat(editor.path()).isEqualTo(source.toAbsolutePath().normalize());
            assertThat(editor.component()).isNotNull();
            assertThat(editor.tab()).isNotNull();
            assertThat(editor.textArea()).isNotNull();
            assertThat(editor.document()).isNotNull();
            assertThat(editor.isModified()).isFalse();
          } catch (IOException error) {
            throw new AssertionError(error);
          }
        });
  }

  @Test
  void saveWritesTheContentBackToDisk() throws Exception {
    Path source = Files.writeString(tempDirectory.resolve("app.py"), "print('old')");
    SwingUtilities.invokeAndWait(
        () -> {
          try (SimpleEditor editor = SimpleEditor.open(source)) {
            editor.textArea().setText("print('new')");
            editor.save();
            assertThat(Files.readString(source)).isEqualTo("print('new')");
          } catch (IOException error) {
            throw new AssertionError(error);
          }
        });
  }

  @Test
  void saveAsWritesToTheTargetAndLoadsIt() throws Exception {
    Path source = Files.writeString(tempDirectory.resolve("notes.txt"), "text");
    Path target = tempDirectory.resolve("renamed.md");
    SwingUtilities.invokeAndWait(
        () -> {
          try (SimpleEditor editor = SimpleEditor.open(source)) {
            editor.textArea().setText("updated");
            editor.saveAs(target);
            assertThat(Files.readString(target)).isEqualTo("updated");
            assertThat(editor.textArea().getText()).isEqualTo("updated");
          } catch (IOException error) {
            throw new AssertionError(error);
          }
        });
  }

  @Test
  void closeIsIdempotentAndClosedEditorsRejectUse() throws Exception {
    Path source = Files.writeString(tempDirectory.resolve("main.rs"), "fn main() {}");
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            SimpleEditor editor = SimpleEditor.open(source);
            editor.close();
            editor.close();
            assertThatIllegalStateException().isThrownBy(editor::save);
            assertThatIllegalStateException().isThrownBy(editor::startLanguageServer);
          } catch (IOException error) {
            throw new AssertionError(error);
          }
        });
  }

  @Test
  void saveAsRejectsNullTarget() throws Exception {
    Path source = Files.writeString(tempDirectory.resolve("main.go"), "package main");
    SwingUtilities.invokeAndWait(
        () -> {
          try (SimpleEditor editor = SimpleEditor.open(source)) {
            assertThatNullPointerException().isThrownBy(() -> editor.saveAs(null));
          } catch (IOException error) {
            throw new AssertionError(error);
          }
        });
  }
}
