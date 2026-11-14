# Isolated modules

The fat JAR contains the whole application, but each subsystem can be used on its
own. This page describes what starts, what does not, and which resources must be
closed.

## RSyntaxTextArea only

Use `Editors` or `Editor` directly:

```java
import io.simpleide.api.Editors;
import io.simpleide.ui.editor.Editor;
import java.nio.file.Path;
import javax.swing.JScrollPane;

Editor editor = Editors.open(Path.of("Main.java"));
JScrollPane scroll = new JScrollPane(editor);
```

What starts:

- A Swing editor component.
- RSyntaxTextArea's syntax highlighting, folding and theme support.
- No thread manager, no language toolchain discovery, no LSP server and no
  terminal process.

Close: nothing. The editor has no owned process. Dispose the containing window
normally.

If you need a scroll pane with line numbers, use `Editors.scrollPane(editor)`;
line numbers are disabled by default in that helper. `EditorTab` enables them
when it creates its own layout.

## Editor tab without LSP

`SimpleEditor.open(path)` creates a tab with search, line numbers and local
language support. It does not start a language server until
`startLanguageServer()` is called.

```java
import io.simpleide.api.SimpleEditor;
import java.nio.file.Path;

SimpleEditor editor = SimpleEditor.open(Path.of("hello.py"));
frame.add(editor.component());
```

What starts:

- Swing components.
- A `LanguageServerManager` object, but no server process and no thread manager
  until LSP is requested.

Close: call `editor.close()`. This saves nothing automatically; call
`editor.save()` first if the content should be persisted.

## Thread manager only

```java
import io.simpleide.api.Threads;

Threads.background(() -> doWork());
Threads.onEdt(() -> updateUi());
```

What starts:

- The process-wide `ThreadMaster` singleton.
- Its worker threads and scheduler.

Close: `Threads.shutdown()` only if this application owns the whole JVM
lifecycle. The singleton cannot be initialized again after shutdown. In a
library or plugin, do not call it; remove individual nodes or pools instead.

## Language runner only

```java
import io.simpleide.api.Languages;
import io.simpleide.language.AbstractLanguage;
import java.nio.file.Path;

AbstractLanguage language = Languages.start(Path.of("Main.java"));
// read Output, optionally write Input
Languages.stop(language);
```

What starts:

- The thread manager.
- Toolchain discovery for available languages on first use.
- One managed process for the selected source file.

Close: call `Languages.stop(language)` for a forced stop, or let the process
finish and close its input. The static `Input` and `Output` queues are shared by
all language instances, so avoid concurrent interactive runs.

See [Languages](languages.md).

## LSP client only

The lowest-level supported path is `LanguageServerManager` plus `LspClient`.
This is useful when you have your own editor component.

```java
import io.simpleide.api.Threads;
import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.lsp.core.LanguageServerManager;
import java.nio.file.Path;
import org.eclipse.lsp4j.TextDocumentItem;

Threads.master();

LanguageServerManager manager =
    new LanguageServerManager(status -> System.out.println(status.state()));
Document document = new Document(Path.of("Main.java"), "java");

LspClient client =
    manager
        .connect(document, diagnostics -> System.out.println(diagnostics.getDiagnostics()))
        .join();

client.didOpen(new TextDocumentItem(document.getUri(), "java", 1, sourceText));
```

What starts:

- The thread manager.
- Language-server discovery, optional installation and a server process.
- LSP4J message handling.

Close: call `client.close()` and `manager.close()`. The manager owns all clients
it created and closes them again in `close()`; calling both is safe. If you use
`SimpleEditor`, it owns the manager and closes it for you.

Important: `LanguageServerManager.connect` does not call `didOpen`. That is
editor-specific: `EditorTab` sends the current text after connecting. A custom
editor must send `didOpen`, `didChange`, `didSave` and `didClose` itself.

See [LSP](lsp.md).

## Terminal only

```java
import io.simpleide.api.Terminals;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;

TerminalPanel panel = Terminals.panel();
BusyBoxTerminalController controller = Terminals.controller(panel);
```

What starts:

- The thread manager.
- A dedicated managed worker pool for terminal sessions.
- A BusyBox process when a session is opened.

Close: dispose the panel/controller according to the host window lifecycle.
Closing the terminal panel stops its sessions and releases the worker pool. Check
the source of `BusyBoxTerminalController` for the exact `close`/`dispose`
methods available in your version.

See [Terminal](terminal.md).

## UI helpers only

The `io.simpleide.ui.*` packages contain small helpers for popups, icons,
themes and layouts. They create Swing components and do not start processes.
They may read application preferences. See [UI helpers](ui.md).

## Combining modules

- `SimpleEditor` already combines editor, local language support and LSP.
- `Languages` and `Threads` share the same manager, so language processes appear
  in the same thread tree as your own background tasks.
- `Terminals.controller` uses the shared manager and adds a dedicated pool.
- You can use `Languages` without `SimpleEditor`; you only need a file path.
- You can use `Editors` without `Threads`; this is the smallest dependency
  footprint at runtime even though the JAR contains everything.

## Common lifecycle errors

| Symptom | Cause | Fix |
| --- | --- | --- |
| `IllegalStateException: ThreadMaster is shut down` | `Threads.shutdown()` was called | Restart the JVM; do not shut down a shared manager |
| LSP diagnostics never arrive | `didOpen` was not sent | Send `didOpen` after connecting or use `SimpleEditor` |
| Language server stays alive after closing the window | Manager/editor not closed | Call `close()` on the owner |
| Terminal does not start | Unsupported OS/architecture | Check `Terminals.isSupported()` |
| Two programs read each other's input | Shared static `Input`/`Output` | Run one interactive program at a time |
