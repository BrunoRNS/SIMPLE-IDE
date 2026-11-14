# Simple IDE API

Simple IDE exposes its editor, thread manager, language runners, LSP client,
terminal and small UI helpers as reusable Java components. The public facade is
in `io.simpleide.api`; the underlying classes remain available when a facade does
not cover a use case.

## Contents

- [Getting started](getting-started.md) — add the dependency and run the first
  integration.
- [Artifacts and imports](artifacts.md) — release JAR, JitPack and manual
  installation.
- [Isolated modules](isolated-modules.md) — use only one subsystem.
- [Standalone RSyntaxTextArea](rsyntax.md) — editor without threads or LSP.
- [Embedded editor](editor.md) — search, line numbers, local completion and LSP.
- [Threading](threading.md) — EDT tasks, background tasks and managed pools.
- [Languages](languages.md) — discovery, execution, input and output.
- [Language Server Protocol](lsp.md) — server discovery, installation and use.
- [Terminal](terminal.md) — embedded BusyBox terminal.
- [UI helpers](ui.md) — popups, icons, themes and layouts.
- [Examples](examples.md) — complete copy-paste examples.

## API at a glance

| Facade | Responsibility | Starts threads? | Starts Swing? |
| --- | --- | --- | --- |
| `io.simpleide.api.Editors` | Creates RSyntaxTextArea-based editors | No | Creates Swing components |
| `io.simpleide.api.SimpleEditor` | Full editor tab with local support and LSP | Only when LSP starts | Creates Swing components |
| `io.simpleide.api.Threads` | Manages EDT and background tasks | Yes | No application frame |
| `io.simpleide.api.Languages` | Language discovery and execution | Yes | No |
| `io.simpleide.api.Terminals` | BusyBox terminal panel and controller | Yes | Creates Swing components |
| `io.simpleide.api.SimpleIDE` | Convenience entry point | Yes, on demand | Launches full IDE |

## Core types

| Type | Package | Purpose |
| --- | --- | --- |
| `Editor` | `io.simpleide.ui.editor` | RSyntaxTextArea subclass with Simple IDE defaults |
| `EditorTab` | `io.simpleide.ui.editor` | Editor plus search panel, line numbers and tab state |
| `SimpleEditor` | `io.simpleide.api` | Embeddable wrapper around `EditorTab` and LSP |
| `ThreadMaster` | `io.simpleide.thread` | Singleton thread tree and schedulers |
| `RunnableNode` | `io.simpleide.thread.nodes.runnable` | Background task node |
| `EDTNode` | `io.simpleide.thread.nodes.edt` | Swing EDT task node |
| `ManagedThreadPool` | `io.simpleide.thread` | Named worker pool registered in the thread tree |
| `LangConfig` | `io.simpleide.language.config` | Extension-to-language registry |
| `AbstractLanguage` | `io.simpleide.language` | Runnable language process abstraction |
| `LanguageServerManager` | `io.simpleide.lsp.core` | Shared LSP server lifecycle |
| `LspClient` | `io.simpleide.lsp.client` | LSP4J client connected to one server |
| `TerminalPanel` | `io.simpleide.ui.terminal` | Terminal tabs and controls |
| `BusyBoxTerminalController` | `io.simpleide.ui.terminal` | Session and terminal lifecycle |

## Design rules

1. **Use the facades first.** They initialize shared state correctly and keep
   application lifecycle details out of callers.
2. **Use lower-level classes for advanced control.** For example, use
   `LanguageServerManager` directly when you need a specific workspace root or
   diagnostics handler.
3. **Do not create a second application frame unless you need one.** The facade
   classes return components; you decide where they are mounted.
4. **Call `close()` on anything that starts a process.** `SimpleEditor`,
   `LspClient`, `LanguageServerManager`, `ManagedThreadPool` and language
   instances own operating-system resources.
5. **Do not shut down `ThreadMaster` if you do not own the application.** The
   singleton cannot be initialized again after shutdown.
6. **Run Swing updates on the EDT.** Use `Threads.onEdt(...)` or
   `ThreadMaster.executeOnEDT(...)` from background tasks.

## Threading model in one paragraph

`ThreadMaster` owns a tree of nodes and two kinds of scheduling: EDT nodes run
through Swing's event queue, and background nodes run on a small managed worker
pool. Registering a node does not necessarily run it immediately; `initialize()`
starts the tree, and later registrations are scheduled as they arrive. The
manager has a bounded queue and a small worker count, so a task must not wait for
another task in the same pool to finish. See [Threading](threading.md) and
[THREADMASTER.md](../reference/THREADMASTER.md).

## Lifecycle summary

| Operation | What to call | What it does |
| --- | --- | --- |
| Start the full application | `SimpleIDE.launch()` | Creates the main frame and initializes shared services |
| Open a standalone editor | `SimpleEditor.open(path)` | Creates an editor tab; no LSP until requested |
| Start an editor language server | `SimpleEditor.startLanguageServer()` | Filters registry, installs if needed, starts server |
| Run a source file | `Languages.start(path)` | Resolves the language and starts a managed process |
| Send input to a running program | `Input.getInstance().set(line)` | Queues a line for the active language process |
| Read program output | `Output.getInstance().get(seconds)` | Polls the shared output queue |
| Add a background task | `Threads.background(action)` | Registers a `RunnableNode` |
| Update Swing from a task | `Threads.onEdt(action)` | Queues an EDT task |
| Create a worker pool | `Threads.pool("name", workers)` | Registers a named pool |
| Create a terminal | `Terminals.panel()` + `Terminals.controller(panel)` | Creates terminal UI and lifecycle controller |
| Stop a language | `Languages.stop(language)` | Force-stops the process |
| Stop everything you own | `close()` on the component | Releases processes, executors and LSP clients |

## Versioning

The API follows the application version in `simpleide.properties`. Breaking
changes should be documented in the release notes. Source and binary
compatibility is not guaranteed across major versions.
