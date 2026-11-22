# Embedded editor

`io.simpleide.api.SimpleEditor` is a standalone editor tab. It combines an
`Editor` with `EditorSearchPanel`, an `RTextScrollPane`, a close button, save
tracking and optional LSP support.

## Create and mount

```java
import io.simpleide.api.SimpleEditor;
import io.simpleide.lsp.LspStatus;
import java.nio.file.Path;
import javax.swing.JFrame;

SimpleEditor editor =
    SimpleEditor.open(
        Path.of("src/main/java/Example.java"),
        (LspStatus status) -> System.out.println(status.state()));

JFrame frame = new JFrame("Embedded editor");
frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
frame.add(editor.component());
frame.setSize(1100, 750);
frame.setVisible(true);
```

The component returned by `component()` is the `EditorTab`. It can be added to a
`JTabbedPane`, `JSplitPane`, `JPanel` or any other Swing container.

`SimpleEditor.open(path)` uses the same content detection as the desktop IDE:
UTF-8 text up to 2 MiB uses the regular editor, larger UTF-8 files use the
read-only, paginated `LargeTextViewer`, ImageIO-readable files use `ImageViewer`,
and other files use `HexEditor`. For files displayed in another viewer,
`textArea()` throws `IllegalStateException`; use `imageViewer()`,
`largeTextViewer()`, or `hexEditor()` as applicable. Language-server and local
language support methods apply only to regular editable text files.

## Image, large-text, and binary viewers

The public `io.simpleide.api.ImageViewer`, `LargeTextViewer`, and `HexEditor`
components can also be created directly:

```java
import io.simpleide.api.HexEditor;
import io.simpleide.api.ImageViewer;
import io.simpleide.api.LargeTextViewer;
import java.nio.file.Files;
import java.nio.file.Path;

ImageViewer image = new ImageViewer(Path.of("assets/logo.webp"));
image.setZoomPercent(150);

Path binaryPath = Path.of("assets/data.bin");
HexEditor binary = new HexEditor(binaryPath);
binary.setByte(0, 0x7f);
if (binary.isModified()) {
  binary.save(binaryPath);
}

Path largeTextPath = Path.of("logs/large.log");
LargeTextViewer largeText = new LargeTextViewer(largeTextPath, Files.size(largeTextPath));
```

`ImageViewer` uses the JDK ImageIO readers and the TwelveMonkeys ImageIO plugins
included in the application, as well as Batik for supported vector formats. It
provides a zoom control and horizontal/vertical scrolling. Decoded images are
subsampled to at most 1 megapixel while preserving the original dimensions, so
zooming a very large image enlarges its bounded preview rather than retaining
the full-resolution raster.

`LargeTextViewer` displays large UTF-8 files in 256 KiB pages. It reads pages on
demand and is intentionally read-only; `largeTextViewer()` returns it from
`SimpleEditor`. `HexEditor(Path)` reads through a 64 KiB chunk cache (at most
four chunks) and the ASCII column renders only visible table rows; non-printable
bytes appear as `.`. Edits are staged in a temporary disk overlay and written
when `save(path)` is called. `byteCount()`, `getByte(offset)`,
`setByte(offset, value)`, `isModified()` and `save(path)` support large files.
`bytes()` is available only for files up to 16 MiB and otherwise throws
`IllegalStateException`. Copy and Paste are limited to 1 MiB of clipboard text.
`HexEditor` implements `AutoCloseable`; call `close()` when discarding a
file-backed instance to release its file channel and temporary edit overlay.
Files detected as neither readable text nor an ImageIO-supported image open in
the hex editor.

## Methods

| Method | Purpose |
| --- | --- |
| `open(Path)` | Open a file with a no-op LSP status listener |
| `open(Path, Consumer<LspStatus>)` | Open a file and observe LSP lifecycle |
| `component()` | Return the embeddable `JComponent` |
| `tab()` | Return the underlying `EditorTab` |
| `textArea()` | Return the `Editor`; throws when a specialized viewer is active |
| `imageViewer()` | Return the image viewer for image files, otherwise `null` |
| `largeTextViewer()` | Return the paginated read-only viewer for large text, otherwise `null` |
| `hexEditor()` | Return the hex editor for binary files, otherwise `null` |
| `document()` | Return the LSP `Document` |
| `path()` | Return the normalized source path |
| `enableLocalLanguageSupport()` | Install local completion when no LSP is connected |
| `startLanguageServer()` | Connect the matching language server lazily |
| `save()` | Write the editor content to disk |
| `saveAs(target)` | Write the content to a different file and load it |
| `isModified()` | Report unsaved changes |
| `close()` | Release the editor and its language server |

## Local language support

`EditorTab` enables local language support when the tab is created, unless the
application starts an LSP connection first. Local support includes:

- RSTA Language Support for supported syntax styles.
- Java JRE class lookup for Java completion.
- Keyword completion as a fallback.
- Auto-completion popup.

Call `enableLocalLanguageSupport()` only if you create the tab yourself or need
to restore local support after an LSP server stops. It does nothing when LSP is
already connected or local support is already registered.

## Start a language server

```java
editor
    .startLanguageServer()
    .thenAccept(client -> System.out.println("Connected to " + client))
    .exceptionally(error -> {
      System.err.println("LSP unavailable: " + error.getMessage());
      return null;
    });
```

`startLanguageServer()` returns the same future on repeated calls. The future
completes after the server has initialized and `EditorTab` has sent `didOpen`.
The server is closed by `SimpleEditor.close()`.

## Status listener

The listener receives `LspStatus` records:

```java
record LspStatus(String id, String serverName, State state) {}
```

States:

| State | Meaning |
| --- | --- |
| `CHECKING_VERSION` | Resolving the installed or latest server version |
| `DOWNLOADING` | Downloading an archive |
| `EXTRACTING` | Extracting the archive |
| `INSTALLING` | Running a package manager or install command |
| `STARTING` | Starting the server process |
| `RUNNING` | Server initialized |
| `FAILED` | Installation or startup failed |
| `IDLE` | Server stopped or manager closed |

Use the listener to show progress in a status bar. Do not perform blocking work
inside it; it may be called from a background thread.

## Saving and modification state

```java
if (editor.isModified()) {
  editor.save();
}
```

`save()` writes editable text through the tab's `File` model. It does not prompt
and does not create parent directories. Large text is read-only and `save()`
reports that limitation with `IOException`. Handle `IOException` in the caller
and show an error dialog if needed.

`isModified()` compares the current text with the last saved text. It becomes
`true` on user edits and `false` after a successful save.

`saveAs(target)` writes the current text to a new path, loads it into the editor
(switching the syntax style to match the target extension) and returns without
touching the original file. It rejects a `null` target and, like `save()`, does
not create parent directories. Use it for "Save a copy" workflows:

```java
editor.saveAs(Path.of("backup/Main.bak.java"));
```

## Search and replace

The search panel is part of `EditorTab` and is controlled by
`EditorSearchPanel`. Its `showSearchPanel` method is package-private, so external
callers use the standard keyboard shortcut or the editor actions registered by
the application. If you need a public control, add a small wrapper in your
application that calls the panel through `EditorTab` methods or contribute a
public API method.

## LSP features available through `EditorTab`

- Diagnostics are published into the editor's parser and rendered as squiggles.
- Completion is requested after edits and triggered through auto-completion.
- Go-to-definition is available through `definitionAtCaret()`.
- Navigation to an LSP range is available through `goToRange(Range)`.
- Document formatting is available through `LspEditorSupport.formatDocument()`.
- Save notifications are sent through `LspEditorSupport.documentSaved()`.

`EditorActions` wires these operations to key bindings and menu actions in the
desktop application. An embedding application can call the methods directly.

## Lifecycle

```java
try (SimpleEditor editor = SimpleEditor.open(path)) {
  // use editor
}
```

`close()` is idempotent. It closes the tab and the `LanguageServerManager`. A
language server process may take a short grace period to stop; the manager
waits up to ten seconds and then logs a warning.

Do not reuse a closed `SimpleEditor`. `startLanguageServer()`, `save()` and
`saveAs()` throw
`IllegalStateException` after close.

## Threading

- Reading the file happens on the calling thread.
- Local completion runs on the EDT.
- LSP requests run on LSP4J and Simple IDE worker threads.
- Diagnostics and completion results are applied on the EDT.
- The status listener may run on either the EDT or a worker thread.

Do not block the EDT while waiting for `startLanguageServer()`. Use the returned
future.

## Differences from the desktop tab

The desktop application adds the file tree, main window, terminal and menu
actions. `SimpleEditor` intentionally leaves those out. It does not register the
file with the application's open-tab list and it does not ask before closing a
modified file.
