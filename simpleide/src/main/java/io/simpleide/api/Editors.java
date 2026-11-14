package io.simpleide.api;

import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TypeFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

/** Creates standalone RSyntaxTextArea-based editors without starting the desktop application. */
public final class Editors {

  private Editors() {}

  /**
   * Creates an empty editor with the application font and editing defaults.
   *
   * @return a new editor
   */
  public static Editor create() {
    return new Editor();
  }

  /**
   * Creates an editor pre-loaded with content and the application editing defaults.
   *
   * @param content initial editor content
   * @return a configured editor
   */
  public static Editor create(String content) {
    Editor editor = new Editor();
    editor.setText(Objects.requireNonNull(content, "content"));
    return editor;
  }

  /**
   * Creates an editor and loads a source file.
   *
   * @param file source file to read
   * @return a configured editor
   * @throws IOException when the file cannot be read
   */
  public static Editor open(Path file) throws IOException {
    Editor editor = new Editor();
    editor.loadFile(new File(Objects.requireNonNull(file, "file")));
    return editor;
  }

  /**
   * Wraps an editor in a scroll pane with line numbers disabled by default.
   *
   * @param editor editor to wrap
   * @return a new scroll pane
   */
  public static RTextScrollPane scrollPane(Editor editor) {
    return new RTextScrollPane(Objects.requireNonNull(editor, "editor"));
  }

  /**
   * Applies the syntax style for a file path to an existing editor.
   *
   * @param editor editor to update
   * @param file path used to select the syntax style
   */
  public static void applySyntaxStyle(Editor editor, Path file) {
    Objects.requireNonNull(editor, "editor").setSyntaxStyle(File.typeOf(file));
  }

  /**
   * Applies a resolved file type to an existing editor.
   *
   * @param editor editor to update
   * @param type file type used to select the syntax style
   */
  public static void applySyntaxStyle(Editor editor, TypeFile type) {
    Objects.requireNonNull(editor, "editor").setSyntaxStyle(type);
  }

  /**
   * Returns the RSyntaxTextArea syntax style constant for a file path.
   *
   * @param file path used to select the syntax style
   * @return an RSyntaxTextArea syntax style constant
   */
  public static String syntaxStyleFor(Path file) {
    return Editor.syntaxStyleFor(File.typeOf(file));
  }

  /**
   * Returns the base RSyntaxTextArea class used by the application. This method exists so consumers
   * can verify type compatibility without depending on the concrete editor class.
   *
   * @return the RSyntaxTextArea class
   */
  public static Class<? extends RSyntaxTextArea> textAreaType() {
    return Editor.class;
  }

  /**
   * Resolves the file type used by the editor for a path.
   *
   * @param file path to inspect
   * @return the matching file type
   */
  public static TypeFile typeOf(Path file) {
    return File.typeOf(file);
  }
}
