# Simple IDE API

Simple IDE exposes its editor, thread manager, language runners, LSP client,
terminal and small UI helpers as reusable Java components. The public facade is
in `io.simpleide.api`; the underlying classes remain available when a facade does
not cover a use case.

## Contents

- [Getting started](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Getting-Started) — add the dependency and run the first
  integration.
- [Artifacts and imports](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Artifacts) — release JAR, JitPack and manual
  installation.
- [Isolated modules](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Isolated-Modules) — use only one subsystem.
- [Standalone RSyntaxTextArea](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-RSyntax) — editor without threads or LSP.
- [Embedded editor](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Editor) — search, line numbers, local completion and LSP.
- [Audio and video](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Editor#audio-and-video) — optional JavaFX media playback, platform support and fallback. See also [JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media) for the `nojavafx`/`javafx22`/`javafx23` consumer contract.
- [Threading](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Threading) — EDT tasks, background tasks and managed pools.
- [Languages](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Languages) — discovery, execution, input and output.
- [Language Server Protocol](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-LSP) — server discovery, installation and use.
- [Terminal](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Terminal) — embedded BusyBox terminal and I/O tab.
- [Debug Adapter Protocol](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-DAP) — adapter registry, installation and debug sessions.
- [UI helpers](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI) — popups, icons, themes and layouts.
- [Application bootstrap and frame](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-Application) — `MainFrame`, layout helpers and startup.
- [Logging console](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-Logging) — Swing log panel and Logback appender.
- [Popups](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-Popups) — info, success, warning, error and input dialogs.
- [Menu bar and themes](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-Menubar) — File, Settings, Execute and Help menus, theme manager.
- [Project file tree](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-File-Tree) — tree model, icons, context actions and watcher.
- [Shared utilities](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Utilities) — files, resources, `PATH` lookup, map conversion.
- [Bytecode compiler](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Compiler) — standalone Java, Kotlin and Groovy execution.
- [Examples](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Examples) — complete copy-paste examples.

## API at a glance

| Facade | Responsibility | Starts threads? | Starts Swing? |
| --- | --- | --- | --- |
| `io.simpleide.api.Editors` | Creates RSyntaxTextArea-based editors | No | Creates Swing components |
| `io.simpleide.api.SimpleEditor` | Text, paged large-text, image, audio, video and binary editor tab | Only when LSP starts | Creates Swing components |
| `io.simpleide.api.ImageViewer` | ImageIO image viewer with zoom and scrolling | No | Creates Swing components |
| `io.simpleide.api.MediaPlayback` | Platform-neutral audio/video player interface | No | Creates Swing components |
| `io.simpleide.api.MediaPlaybacks` | Creates the JavaFX media players, or reports them unavailable | No | Creates Swing components |
| `io.simpleide.api.LargeTextViewer` | Read-only paged viewer for large UTF-8 files | Page reads on demand | Creates Swing components |
| `io.simpleide.api.HexEditor` | Chunked binary/hex editor with visible ASCII rows | No | Creates Swing components |
| `io.simpleide.api.Threads` | Manages EDT and background tasks | Yes | No application frame |
| `io.simpleide.api.Languages` | Language discovery and execution | Yes | No |
| `io.simpleide.api.LanguageServers` | Connects files to shared language servers and delivers diagnostics | Yes | No |
| `io.simpleide.api.Debugger` | Starts standalone and language-server debug sessions | Yes, on start | No |
| `io.simpleide.api.Terminals` | BusyBox terminal panel and controller | Yes | Creates Swing components |
| `io.simpleide.api.SimpleIDE` | Convenience entry point | Yes, on demand | Launches full IDE |

## Core types

| Type | Package | Purpose |
| --- | --- | --- |
| `Editor` | `io.simpleide.ui.editor` | RSyntaxTextArea subclass with Simple IDE defaults |
| `EditorTab` | `io.simpleide.ui.editor` | Editor plus search panel, line numbers and tab state |
| `SimpleEditor` | `io.simpleide.api` | Embeddable wrapper around `EditorTab` and LSP |
| `MediaPlayback` | `io.simpleide.api` | Audio/video player contract used by `SimpleEditor` |
| `MediaPlaybacks` | `io.simpleide.api` | JavaFX media factory with platform availability detection |
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
| `IOPanel` | `io.simpleide.ui.io` | Program input, coloured output and debug controls |
| `DapRegistry` | `io.simpleide.dap` | Debug adapter registry and platform matching |
| `DapSession` | `io.simpleide.dap` | Running debug adapter connection and execution controls |
| `Tree` | `io.simpleide.ui.treefile` | Project file tree component |
| `TreeFile` | `io.simpleide.ui.treefile` | Base entry of the project tree |
| `ThemeManager` | `io.simpleide.ui.menubar` | Look and feel and editor theme singleton |
| `PopUP` | `io.simpleide.frame.popup` | Base class for managed dialogs |
| `LogPanel` | `io.simpleide.frame.log` | In-window debug console |
| `FileService` | `io.simpleide.utils.service` | File and directory operations for the tree |
| `MainFrame` | `io.simpleide.frame` | Desktop window wiring every component |
| `Runner` | `io.simplebytecode.compiler` | Standalone source dispatcher for JVM languages |

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
another task in the same pool to finish. See [Threading](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Threading) and
[THREADMASTER.md](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-ThreadMaster).

## Lifecycle summary

| Operation | What to call | What it does |
| --- | --- | --- |
| Start the full application | `SimpleIDE.launch()` | Creates the main frame and initializes shared services |
| Open a standalone editor | `SimpleEditor.open(path)` | Creates an editor tab; no LSP until requested |
| Start an editor language server | `SimpleEditor.startLanguageServer()` | Filters registry, installs if needed, starts server |
| Run a source file | `Languages.start(path)` | Resolves the language and starts a managed process |
| Start debugging | `DapManager.start(path, breakpoints, listener)` | Installs an adapter and launches a DAP session |
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
