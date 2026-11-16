# Standalone RSyntaxTextArea

`io.simpleide.ui.editor.Editor` is a subclass of
`org.fife.ui.rsyntaxtextarea.RSyntaxTextArea`. It adds only a few defaults:

- The UI font from `UIManager` (`TextArea.font`).
- Code folding.
- Auto-indent.
- Bracket matching animation.
- Automatic closing of curly braces.
- A file-extension to syntax-style mapping.

Everything else comes from RSyntaxTextArea.

## Create an empty editor

```java
import io.simpleide.api.Editors;
import io.simpleide.ui.editor.Editor;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

Editor editor = Editors.create();          // empty editor
Editor buffer = Editors.create("hello");   // pre-loaded content
JFrame frame = new JFrame("Editor");
frame.add(new JScrollPane(editor));
frame.setSize(800, 600);
frame.setVisible(true);
```

`Editors.create(String content)` rejects a `null` content. The editor starts
with no syntax style; apply one with `Editors.applySyntaxStyle(editor,
io.simpleide.ui.treefile.TypeFile.JAVA)` when you know the file type up
front.

## Open a file

```java
import io.simpleide.api.Editors;
import java.nio.file.Path;

Editor editor = Editors.open(Path.of("src/main/java/Example.java"));
```

`Editors.open` reads the whole file into memory with `File.getCode()`. It is
intended for source files, not for very large logs or binary files.

## Set the syntax style

```java
import io.simpleide.api.Editors;
import io.simpleide.ui.treefile.TypeFile;

editor.setSyntaxStyle(TypeFile.PYTHON);
editor.setSyntaxEditingStyle(Editors.syntaxStyleFor(Path.of("script.py")));
```

`Editor.syntaxStyleFor(TypeFile)` returns the RSyntaxTextArea constant. The
supported mappings are:

| Type | RSyntaxTextArea style |
| --- | --- |
| `JAVA` | `text/java` |
| `CSHARP` | `text/csharp` |
| `GROOVY` | `text/groovy` |
| `KOTLIN` | `text/kotlin` |
| `JAVASCRIPT` | `text/javascript` |
| `GO` | `text/go` |
| `RUST` | `text/rust` |
| `PYTHON` | `text/python` |
| `C` | `text/c` |
| `CPP` | `text/cpp` |
| `TXT`, `BINARY` | `text/plain` |

## Wrap in a scroll pane

```java
import io.simpleide.api.Editors;
import org.fife.ui.rtextarea.RTextScrollPane;

RTextScrollPane scroll = Editors.scrollPane(editor);
```

`RTextScrollPane` provides line numbers, folding and current-line highlighting.
`Editors.scrollPane` creates it with line numbers disabled. Enable them yourself
if you want the editor-tab look:

```java
scroll.setLineNumbersEnabled(true);
scroll.setFoldIndicatorEnabled(true);
```

## Load and save manually

`Editor` is a Swing text component, so the usual RSyntaxTextArea methods work:

```java
editor.setText("class Main {}\n");
editor.setCaretPosition(0);

String text = editor.getText();
```

Simple IDE's higher-level `EditorTab` handles saving through its `File` model.
For a standalone editor, write the text yourself or use `EditorTab`.

## Themes and fonts

The editor inherits the current Swing look and feel. For FlatLaf themes:

```java
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.UIManager;

FlatDarkLaf.setup();
// create the editor after installing the look and feel
Editor editor = Editors.create();
```

`Editor.updateUI()` reapplies the `TextArea.font` after a look-and-feel change.
See [UI helpers](ui.md) for the application theme manager.

## Local completion

`Editor` alone does not install RSTA language support or auto-completion. Use
`SimpleEditor` for local completion, or register support yourself:

```java
import org.fife.rsta.ac.LanguageSupportFactory;

LanguageSupportFactory.get().register(editor);
```

The auto-completion popup still needs an `AutoCompletion` instance and a
completion provider from the matching Language Support module. That setup is
language-specific; the application's `EditorTab.enableLocalLanguageSupport()`
contains the supported configuration, including Java JRE class lookup. Use
`SimpleEditor` if you do not want to reproduce it.

## Lifecycle

`Editor` has no owned process and no background thread. Dispose the containing
window normally. If you install language support or auto-completion, unregister
or dispose it with the editor if your host application supports reloading the
look and feel repeatedly.

## When to use `SimpleEditor` instead

Use `SimpleEditor` when you need:

- Search and replace panel.
- Line numbers and folding configured.
- Tab name, close button and save tracking.
- Local completion enabled automatically.
- Optional LSP connection with diagnostics and go-to-definition.

Use `Editor` when you only need a text area and want full control over the
surrounding UI.
