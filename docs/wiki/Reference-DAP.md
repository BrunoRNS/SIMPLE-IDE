# Debug Adapter Protocol (DAP)

Simple IDE supports interactive debugging through the Debug Adapter Protocol.
Adapters are either external processes that speak DAP over standard
input/output or TCP, or a debug server hosted by a language server. The supported
adapter definitions and their per-platform download metadata live in
[`simpleide/src/main/resources/io/simpleide/dap/registry.json`](../../simpleide/src/main/resources/io/simpleide/dap/registry.json)
(mirrored as `dap_registry.json`).

Debugging is available for **JavaScript, Python, Go and Java**. Opening another
file type and invoking Debug reports that the language is not debuggable; it does
not disable debugging for the supported languages.

## Components

| Type | Responsibility |
| --- | --- |
| `DapRegistry` | Parses the bundled registry and matches adapters to the host platform |
| `DapAdapter` | Adapter command, arguments, transport, launch properties and downloads |
| `DapDownload` | Download URL and archive type for one OS/architecture pair |
| `DapInstaller` | Downloads, extracts and marks an adapter under `~/.simpleide/dap` |
| `DapManager` | Resolves, installs and starts a session for a source file |
| `DapSession` | Connects to the adapter, sends `initialize`/`launch` and exposes execution controls |
| `JavaDebugBridge` | Drives the Java debug server hosted by the JDT language server |

## Registry

Each adapter declares an id, name, version, language id, launch command, transport
(`stdio`, `tcp` or `jdtls`), optional launch properties and a download entry per
platform:

```json
{
  "adapters": [
    {
      "id": "js-debug",
      "name": "JavaScript Debug Adapter",
      "version": "1.140.1",
      "languageId": "javascript",
      "command": "node",
      "transport": "tcp",
      "launch": { "type": "pwa-node", "console": "internalConsole" },
      "arguments": ["${installDir}/js-debug/src/dapDebugServer.js", "${port}"],
      "downloads": [
        { "os": "linux", "arch": "x64", "url": "https://example.com/js-debug.tar.gz", "archiveType": "tar.gz" }
      ]
    }
  ]
}
```

Command arguments support `${installDir}`, `${program}` and `${port}`; launch
property strings support `${program}` and `${fileDir}`. Launch values may also be
booleans or numbers (for example Python's `justMyCode: false`), and they are sent
to the adapter with their JSON type preserved. Platform matching uses
`LangPlatform` values: `windows`, `linux` or `macos` combined with `x86`, `x64`,
`arm64` or `aarch64`; `amd64` is accepted as an alias of `x64`.

## Transports per language

| Language | Adapter | Transport | How it starts |
| --- | --- | --- | --- |
| JavaScript | `js-debug` | `tcp` | IDE picks a free port and passes it as an argument |
| Python | `debugpy` | `tcp` | `python3 -m debugpy --listen 127.0.0.1:${port}`; uses `console: integratedTerminal` so `input()` is forwarded through the I/O tab, and `justMyCode: false` so stepping enters library code |
| Go | `delve` | `tcp` | `dlv dap --listen 127.0.0.1:${port} --check-go-version=false`; launches with `mode: debug`, `outputMode: remote` and the source file, so standalone files without a `go.mod` work |
| Java | `vscode-java-debug` in JDT LS | `jdtls` | Language server command, no download |

Adapters that speak DAP on their standard input/output use the `stdio` transport.
Server-style adapters bind different loopback addresses: `debugpy` and `delve`
listen on IPv4 while the JavaScript adapter listens on IPv6, and the IDE tries
both `127.0.0.1` and `::1` when connecting.

### Go and older toolchains

Delve is built against a specific Go release and by default refuses to launch when
the local Go toolchain is older than the one it was built with, answering
`Go version go1.22.2 is too old for this version of Delve`. The bundled adapter is
therefore started with `--check-go-version=false`, so a machine with an older Go
installed can still debug; the IDE only ships one Delve build per platform, and
without the flag debugging would be unusable until Go was upgraded.

Two more delve details matter:

- `dlv dap` does not support `--accept-multiclient`; the flag only produces a
  warning, so it is not passed at all.
- With the default `outputMode`, delve writes the debuggee's standard output to
  its own standard output, where the IDE cannot see it. The Go adapter launches
  with `outputMode: remote`, so the program output arrives as DAP `output` events
  and shows up in the I/O tab like any other language.

## Child debug sessions

The JavaScript adapter launches the program as a nested session: after `launch` it
sends a `startDebugging` request and expects the IDE to open the child session.
`DapSession` opens that child session on the same adapter port, inherits the
breakpoints and forwards its events, and execution controls follow the session
that actually stopped. Thread identifier `0` is accepted, and the `initialized`
event is awaited for at most 90 seconds so adapters that build the program first,
like delve, still start.

## Installing and launching

`DapManager.createDefault()` reads the bundled registry and detects the current
platform. `isSupported()` reports whether an adapter exists; `prepare()`
downloads and extracts it when no matching `.installed` marker is present.
Downloads must use HTTPS and are removed when extraction fails, so a failed
attempt never leaves a partial installation behind.

Sessions always start the program with a `launch` request; `attach` is never used.
The adapter's `runInTerminal` request starts the debuggee with its standard
input, output and error streams connected to the I/O tab through the shared
`Input` and `Output` APIs. For an unknown platform, missing download, or failed
installation, `DapManager.DapUnavailableException` carries a user-facing message
that `MainFrame` shows to the user.

## Java through the JDT language server

Java debugging runs inside Eclipse JDT LS instead of a downloaded adapter:

1. `vscode.java.startDebugSession` starts the debug server and returns its port.
2. `vscode.java.resolveMainClass` resolves the main class of the open file.
3. `vscode.java.resolveClasspath` returns the module and class paths.
4. `DapSession` connects over TCP and sends `launch` with `mainClass`,
   `projectName`, `modulePaths`, `classPaths`, `cwd` and `console`.

The language server already connected to the editor tab is reused; when the tab
has none yet, `EditorManager.connectLanguageServer` connects one. Java debugging
therefore requires an Eclipse JDT language server with the `vscode-java-debug`
plugin loaded. Without it the commands are not registered and the IDE reports
that the language server rejected the command. The IDE downloads the
`vscode-java-debug` bundle and passes it to the server in
`initializationOptions.bundles`; the language server itself still has to be
available.

### Files outside a project

JDT LS does not report a main class for a loose `.java` file, because the file is
not part of any project and nothing compiles it. A Run/Debug request for such a
file therefore compiles the file with the JDK compiler into a temporary
directory, picks the compiled class named like the file (or the first top-level
class), and sends that directory as the only `classPaths` entry. If the file does
not compile, the IDE reports the failure instead of launching a class that does
not exist.

## Breakpoints

Breakpoints are stored per editor in `Editor` and painted in the gutter icon
column through `Gutter.addLineTrackingIcon`. They are visible for every supported
language file.

- Clicking the gutter toggles the breakpoint on the clicked line. The breakpoint
  column is the gutter's icon row header, which is a child component, so the
  mouse listeners are installed on the gutter and on its children.
- The hovered line shows a translucent dot preview; selected lines show a solid
  red dot.
- Toggling a breakpoint (F9 or gutter click) notifies the active `DapSession`
  through `setBreakpoints`, so the adapter always receives the current line set
  for the debugged file.

## Output and controls

Adapter and debuggee output events are published to the shared `Output` queue, so
the I/O tab renders them with the same `[OUTPUT]`, `[ERROR]` and `[DEBUG]`
colours as normal program output. Session milestones appear as `[OUTPUT]`
markers:

| Marker | Meaning |
| --- | --- |
| `[OUTPUT] {INITIALIZED}` | The adapter completed the DAP handshake |
| `[OUTPUT] {WAITING FOR STDIN}` | The debuggee is running and waiting for input; repeated after each forwarded line |
| `[OUTPUT] {FINISHED}` | The session terminated or the user stopped it |

`DapSession` sends `setBreakpoints` for the selected file and exposes `resume`,
`pause`, `stepOver`, `stepInto`, `stepOut` and `close`, which back the debug
buttons shown by `IOPanel` while a debug session is active. Step requests are
ignored while the program is running; the I/O tab prints that execution is
running.

The output pane is read-only but selectable: the text can be copied with
`Ctrl+C` (or `Cmd+C`) and from its context menu, and the same menu offers
*Select All*.

A failed `launch` request ends session startup immediately and its message is
reported to the user. Adapters reject the launch before the debugger exists and
then answer every later request with `No debug session started`, so waiting for
the `initialized` event would otherwise report a misleading error after 90
seconds instead of the real cause, such as a build error or an unsupported Go
version.

## Running without debugging

The I/O tab shows a single **Stop** button while a program started with Run (no
debug session) is alive. It stops the language process through
`AbstractLanguage.forceStop` on a worker thread, so the event dispatch thread is
not blocked while the process dies, and the panel returns to its idle state when
the language reports the interruption.

## Tests

- `DapRegistryTest` covers registry parsing, platform/architecture matching,
  command placeholders and bundled downloads.
- `DapRegistryIntegrationTest` covers that every supported platform resolves to
  an adapter and that every adapter ships downloads for all OS/architecture
  combinations.
- `DapLanguageRegistryTest` covers the four debuggable languages, transports,
  language mapping and that language-server adapters are never installed.
- `DapManagerTest` covers unsupported-platform fallback without network access.
- `DapInstallerTest` covers installed-version markers and cleanup after a
  rejected insecure download.
- `DapSessionIntegrationTest` starts a real adapter process over `stdio` and
  `tcp` and exercises the whole session, including the execution controls, the
  output markers and a rejected `launch` request that is reported immediately.
- `GoDebugAdapterIntegrationTest` debugs a real Go program with the installed
  delve adapter, both inside a module and as a standalone file without a
  `go.mod`: it stops at a breakpoint, resumes and checks the program output. It
  also asserts the adapter runs with `--check-go-version=false` and without
  `--accept-multiclient`.
- `DapIOPanelIntegrationTest` clicks every debug button against a live adapter.
- `JavaDebugBridgeIntegrationTest` runs the Java path against a real TCP adapter
  and a stubbed language-server client, including the loose-file compilation
  fallback and its compile-error message.
- `JavaRealDebugIntegrationTest` debugs a real loose `Main.java` through JDT LS:
  it stops at a breakpoint, resumes and checks the program output. It is skipped
  unless the build enables it with `-Dsimpleide.java.debug.real=true`, which
  `make test-debug` does.
- `JsDebugAdapterIntegrationTest` debugs a real Node.js program with the installed
  `js-debug` adapter (tagged `language-tools`, skipped without `node`, without the
  adapter, or on an operating system and architecture with no registered adapter).
- `IOPanelTest` and `InputForwarderTest` cover output colouring, selectable and
  copyable output, input forwarding and debug-mode controls.
- `IOPanelRunStopIntegrationTest` starts a real long-running program with Run and
  presses the Stop button of the I/O tab, checking that the process is killed and
  that the panel returns to its idle state.
