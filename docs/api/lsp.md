# Language Server Protocol API

Simple IDE uses Eclipse LSP4J and a JSON server registry. The API can:

- Select and install a language server for a language and platform.
- Start the server once per language and workspace root.
- Share the server between documents in the same workspace.
- Forward diagnostics, completion, definition, formatting and save events.
- Report installation and startup status.

The easiest entry point is `SimpleEditor`; this page documents the lower-level
API for embedding applications that have their own editor.

## Components

| Class | Package | Responsibility |
| --- | --- | --- |
| `LanguageServerManager` | `io.simpleide.lsp.core` | Server processes, sharing and lifecycle |
| `Document` | `io.simpleide.lsp.core` | URI, language id, version and modification state |
| `LspClient` | `io.simpleide.lsp.client` | LSP4J client and requests |
| `LspEditorSupport` | `io.simpleide.lsp.client` | Editor integration for a connected client |
| `LspManager` | `io.simpleide.lsp.dowload` | Registry lookup, version selection and command construction |
| `LspInstaller` | `io.simpleide.lsp.dowload` | Download, extraction and install-command execution |
| `LspServer` | `io.simpleide.lsp.dowload` | Registry entry model |
| `LspStatus` | `io.simpleide.lsp` | Installation/startup status record |

## Supported servers

The bundled registry contains entries for:

| Language | Server | Notes |
| --- | --- | --- |
| Java | Eclipse JDT Language Server | Selects the 17+ or 25+ build from the running JDK |
| JavaScript | typescript-language-server | Requires Node.js and npm |
| C# | OmniSharp (Mono) | Requires Mono 6+ with an MSBuild instance; no .NET SDK needed. A `SimpleIdeLooseFiles.csproj` is written for folders without a C# project |
| C, C++ | clangd | Linux, macOS and Windows x64 |
| Groovy | groovy-language-server | Requires Java 17+; built from source on install with `gradlew -p` |
| Kotlin | kotlin-language-server | Requires Java 11+ |
| Python | Pyright | Requires Python 3 and npm |
| Go | gopls | Requires Go 1.26+; pinned gopls 0.16.2 for Go 1.21 to 1.25 |
| Rust | rust-analyzer | Platform-specific builds; needs the `rust-src` component. Loose `.rs` files are passed through `linkedProjects` |

Server definitions live in
`simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json`. Each entry
declares OS, architecture, minimum language version, version command, install
strategy or URL, launch command and arguments.

### Extra Java snippets

When a Java language server is connected, `LspEditorSupport` merges a few
completion items of its own into every completion response: `psvm` and `main`
insert `public static void main(String[] args) {}`, `sout` inserts
`System.out.println()`, `fori` inserts `for (int i = 0; i < 0; i++) {}` and
`scanner` inserts `final static Scanner SC = new Scanner(System.in);` at type
level (or `final Scanner SC = ...` inside a method), adding the
`java.util.Scanner` import through an additional text edit when it is missing.
The items appear in the normal popup next to the server completions and only
insert their text when accepted; they are offered for Java only, never after a
member-access dot, and they are not expanded automatically.

Snippets are only merged into a response that already contains server
completions. An empty or failed response is passed through untouched, so a
broken language server shows an empty popup instead of a list made up of the
IDE's snippets, which used to hide the fact that JDT LS had answered nothing.
`JavaLspSnippetsPopupIntegrationTest` starts the real JDT LS on a loose Java
file, checks that it answers with completions, merges the snippets and asserts
that the popup still lists server items next to `psvm` (tagged `lsp` and
`language-tools`).

`LspCompletionProvider` orders completions by their input text so entries with
repeating labels and different descriptions stay in the list instead of
breaking the sorted search the completion window relies on.

## Connect a server

### Facade

`io.simpleide.api.LanguageServers` wraps the shared manager, the registry and the
file-to-language mapping in a few methods. Servers are installed on first use and
one server process is shared per language and workspace.

```java
import io.simpleide.api.LanguageServers;
import io.simpleide.lsp.client.LspClient;
import java.nio.file.Path;

Path file = Path.of("src/app.py");
if (LanguageServers.isSupported(file)) {
  LspClient client =
      LanguageServers
          .connect(file, diagnostics -> System.out.println(diagnostics.getDiagnostics()))
          .join();
}
// When the embedding application exits:
LanguageServers.shutdown();
```

`LanguageServers.languageIdOf(Path)` exposes the same language mapping used by
the editor, and `disconnect(Path)` removes the diagnostics handler of one file.

### Lower-level manager

```java
import io.simpleide.api.Threads;
import io.simpleide.lsp.LspStatus;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.lsp.core.LanguageServerManager;
import java.nio.file.Path;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

Path file = Path.of("src/main/java/Example.java");
Document document = new Document(file, "java");

LanguageServerManager manager =
    new LanguageServerManager(
        (LspStatus status) -> System.out.println(status.state() + " " + status.serverName()));

LspClient client =
    manager
        .connect(
            document,
            diagnostics -> {
              System.out.println("Diagnostics: " + diagnostics.getDiagnostics().size());
            })
        .join();

Threads.master(); // already initialized by connect in most paths, but safe here
client.didOpen(
    new TextDocumentItem(document.getUri(), document.getLanguageId(), 1, sourceText));
```

`connect` completes after the server starts and initializes. It does not send
`didOpen`; that is the editor's responsibility. `SimpleEditor` sends it
automatically through `EditorTab.connectLanguageServer`.

## Workspace root detection

The manager walks upward from the document directory and stops at the first
directory containing one of these markers:

```text
pom.xml
build.gradle
build.gradle.kts
settings.gradle
settings.gradle.kts
Cargo.toml
package.json
pyproject.toml
setup.py
go.mod
compile_commands.json
```

If no marker is found, the document's parent directory is the root. Documents
with the same language and workspace root share one server process. The manager
keys servers by `(languageId, workspaceRoot)`.

## Status events

```java
record LspStatus(String id, String serverName, State state) {}
```

| State | Trigger |
| --- | --- |
| `CHECKING_VERSION` | Comparing the installed language version with registry requirements |
| `DOWNLOADING` | Downloading an archive |
| `EXTRACTING` | Extracting an archive |
| `INSTALLING` | Running npm, go or another install command |
| `STARTING` | Starting the server process |
| `RUNNING` | Server initialized |
| `FAILED` | Install or start failed |
| `IDLE` | Server stopped or manager closed |

The `id` is `language@workspaceRoot` for manager events, or the caller-provided
status id for direct `LspManager` calls. Do not block in the listener.

## Installation locations

| Purpose | Location |
| --- | --- |
| Server installation | `~/.simpleide/lsp/<installKey>` |
| Fallback install key | `<languageId><version>` |
| Workspace data passed to the server | `~/.cache/simpleide/lsp/<uuid>` |

The workspace data directory name is a UUID derived from the workspace path. The
manager creates it through the server command template (`${workspaceData}`).
Command templates also support `${workspaceRoot}` for servers that take the
workspace path as an argument, such as OmniSharp's `-s` option. Installation uses
a sibling lock per directory, so concurrent editors do not download the same
server twice.

The marker file `.installed` contains the installed version. If the registry
version changes, the manager downloads the new version.

## LSP client operations

`LspClient` exposes the operations used by the editor:

| Method | Purpose |
| --- | --- |
| `getInitialized()` | Future completed when the server is initialized |
| `didOpen(TextDocumentItem)` | Notify the server that a document opened |
| `didChange(uri, version, previousText, text)` | Send incremental or full changes |
| `didSave(uri, text)` | Notify the server that a document was saved |
| `didClose(uri)` | Notify the server that a document closed |
| `completion(CompletionParams)` | Request completion items |
| `definition(DefinitionParams)` | Request definition locations |
| `formatting(DocumentFormattingParams)` | Request formatting edits |
| `close()` | Stop the client and server connection |
| `awaitShutdown(timeout, unit)` | Wait for the server process to stop |

Diagnostics arrive through `textDocument/publishDiagnostics` and are dispatched
to the handler registered with `connect`.

## Completion

`LspEditorSupport` requests completion after edits, maps LSP `CompletionItem`
values to an RSTA completion provider, and applies the selected item. If you use
your own editor:

1. Send `didChange` with the new text and an incremented document version.
2. Map the caret offset to an LSP `Position`.
3. Send a `CompletionParams` request.
4. Apply the chosen item's `textEdit` or `insertText` yourself.

The bundled `LspCompletionProvider` and `LspTextEdit` show the mapping used by
the application.

## Diagnostics

`publishDiagnostics` gives a list of `Diagnostic` values with ranges, severity,
message and optional code/source. `LspDiagnosticParser` converts them to RSTA
squiggle underlines. A custom editor can keep the list and render it in its own
way. Publish an empty list when a file becomes clean; do not leave stale
diagnostics.

JDT LS publishes diagnostics for loose `.java` files that belong to no project;
`LspServerIntegrationTest.reportsSyntaxErrorsInALooseJavaFile` keeps that
behavior covered (tagged `lsp`).

## Definition and formatting

```java
manager
    .connect(document, diagnostics -> {})
    .thenCompose(
        client ->
            client.definition(
                new org.eclipse.lsp4j.DefinitionParams(
                    new org.eclipse.lsp4j.TextDocumentIdentifier(document.getUri()),
                    new org.eclipse.lsp4j.Position(line, character))))
    .thenAccept(
        result -> {
          // Either<List<Location>, List<LocationLink>>
        });
```

Formatting:

```java
client
    .formatting(
        new DocumentFormattingParams(
            new TextDocumentIdentifier(document.getUri()), new FormattingOptions(4, true)))
    .thenAccept(
        edits -> {
          // apply TextEdits in reverse document order
        });
```

`LspEditorSupport.formatDocument()` already applies edits on the EDT.

## Direct registry access

```java
import io.simpleide.lsp.dowload.LspManager;
import io.simpleide.lsp.dowload.LspServer;

LspServer server =
    LspManager.getRegistry().getServers().stream()
        .filter(s -> s.getLanguageId().equals("python"))
        .findFirst()
        .orElseThrow();

System.out.println(server.getName() + " " + server.getVersion());
```

Useful `LspManager` methods:

| Method | Purpose |
| --- | --- |
| `getRegistry()` | Return all server definitions |
| `getComandRun(language, workspaceRoot, statusId, listener)` | Select, install and build the launch command |
| `getFallbackCommandRun(...)` | Try a compatible older server after a version mismatch |
| `getLanguageVersion(language)` | Run the registered version command |
| `LspInstaller.isInstalled(server)` | Check the installation marker |
| `LspInstaller.dowloading(server, statusId, listener)` | Install a server |

The method name `getComandRun` is intentionally kept for compatibility; use it
as written.

## Custom servers

For a new language:

1. Add a `Document` with the language id used by the server (`textDocument/
   languageId`).
2. Add a registry entry with the correct `languageId`, OS, architecture,
   version probe and launch/install commands.
3. Add the editor language id mapping in your own code if you use an editor
   integration similar to `SimpleEditor.languageIdFor`.
4. Test installation on each supported OS. Version probes differ (`java
   -version`, `node --version`, `python --version`, `go version`).

Registry entries are resources, not user configuration. Rebuild the JAR after
editing `registry.json`.

## Security and privacy

- Downloads use HTTPS URLs from the registry and a five-minute timeout.
- Install commands run external package managers. Only use registry entries you
  trust.
- Language servers can read files in the workspace and may send telemetry.
  Review the server's own settings and privacy policy.
- The manager does not execute code from a document by itself; the language
  runner is a separate subsystem.

## Lifecycle

- One `LanguageServerManager` can own several servers for several languages.
- `manager.close()` closes all clients, waits up to ten seconds and logs a
  warning if a server does not stop.
- `client.close()` closes one connection. Do not use the client after closing.
- A closed manager rejects new `connect` calls.
- `SimpleEditor.close()` closes its manager.

Java-based servers use the bundled JDK in packaged builds. On a development
launch, the manager resolves the JDK from `simpleide.lsp.javaHome`,
`simpleide.javaHome`, `JAVA_HOME` or `PATH`; it never assumes that
`java.home/bin/java` exists, because older jpackage images stripped that
launcher.

## Troubleshooting

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Future fails with "Unable to install language server" | Missing toolchain, network failure or unsupported platform | Check the status listener and `~/.simpleide/lsp` |
| Server starts but no diagnostics | `didOpen` not sent | Send `didOpen` after connecting |
| Server re-downloads every start | Registry version changed | Expected; the `.installed` marker stores the version |
| Server does not stop | Server ignores shutdown | `manager.close()` waits ten seconds, then logs |
| Java server picks the wrong build | Running JDK version differs from project | Use a matching JDK or set `-Dsimpleide.lsp.javaHome` (or `-Dsimpleide.javaHome`) |
| Windows C/C++ has no server | Registry supports Windows x64 only | Use x64 or add a registry entry |
