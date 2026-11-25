# Debug Adapter Protocol (DAP)

`io.simpleide.dap` adds debug support to the Execute menu. It uses the Eclipse
LSP4J debug protocol client (`org.eclipse.lsp4j:org.eclipse.lsp4j.debug`) and the
Debug Adapter Protocol, so adapters are either ordinary external processes that
speak DAP over standard input/output or TCP, or a debug server hosted by a
language server.

Debugging is offered for **JavaScript, Python, Go and Java** only. Any other file
reports a short message when Debug is invoked and leaves debugging enabled for
the supported languages.

## Components

| Type | Responsibility |
| --- | --- |
| `DapRegistry` | Parses the bundled `registry.json` and matches adapters to the host platform |
| `DapAdapter` | Adapter command, arguments, transport, launch properties and downloads |
| `DapDownload` | Download URL and archive type for one OS/architecture pair |
| `DapInstaller` | Downloads, extracts and marks an adapter under `~/.simpleide/dap` |
| `DapManager` | Resolves, installs and starts a session for a source file |
| `DapSession` | Connects to the adapter, sends `initialize`/`launch` and exposes execution controls |
| `JavaDebugBridge` | Drives the Java debug server hosted by the JDT language server |

The registry lives at
`simpleide/src/main/resources/io/simpleide/dap/registry.json` (mirrored as
`dap_registry.json`). Each adapter has an id, name, version, language id, launch
command, transport, optional launch properties and a download entry per platform:

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
        {
          "os": "linux",
          "arch": "x64",
          "url": "https://example.com/js-debug.tar.gz",
          "archiveType": "tar.gz"
        }
      ]
    }
  ]
}
```

Command arguments support `${installDir}`, `${program}` and `${port}`, and launch
properties support `${program}` and `${fileDir}`. Platform matching uses
`LangPlatform` values: `windows`, `linux` or `macos` combined with `x86`, `x64`,
`arm64` or `aarch64`; `amd64` is accepted as an alias of `x64`.

## Transports per language

| Language | Adapter | Transport | Notes |
| --- | --- | --- | --- |
| JavaScript | `js-debug` (`js-debug-dap`) | `tcp` | The IDE picks a free port and passes it as the last argument |
| Python | `debugpy` | `tcp` | Started with `-m debugpy --listen 127.0.0.1:${port}` |
| Go | `delve` (`dlv dap`) | `tcp` | Launches with `mode: debug` and the source directory |
| Java | `vscode-java-debug` inside JDT LS | `jdtls` | Reached through the language server, nothing is downloaded by the IDE |

`stdio` remains supported by `DapSession` and is used by adapters that read and
write DAP on their standard input/output.

Server-style adapters bind different loopback addresses: `debugpy` and `delve`
listen on IPv4, while the JavaScript adapter listens on IPv6. `DapSession` tries
`127.0.0.1` and then `::1` on every connection attempt, so all three work
without extra configuration.

## Child debug sessions

Adapters may launch the debuggee as a nested session. The JavaScript adapter does
this: after `launch` it sends a `startDebugging` reverse request whose
configuration identifies a pending runtime target, and the IDE must open a second
session for it. `DapSession` handles the request by opening a child session on the
same adapter port, inheriting the current breakpoints and forwarding events to the
same listener. Execution controls follow the session that actually stopped, so
`resume`, `pause` and the step commands reach the child while it is paused and
return to the parent when it continues or terminates.

Adapters that do not report a thread on `stopped` are queried with `threads`
before a control request is sent. Thread identifier `0` is treated as a valid
thread, because the JavaScript adapter uses it. The `initialized` event is awaited
for at most five seconds before `launch`, so adapters that emit it after the
launch request still start correctly.

## Installing and launching

`DapManager.createDefault()` reads the bundled registry and detects the current
platform. `isSupported()` reports whether an adapter exists; `prepare()` installs
an adapter that is downloaded as a process. Downloads must use HTTPS and are
removed when extraction fails, so a failed attempt never leaves a partial
installation behind.

```java
DapManager manager = DapManager.createDefault();
if (manager.supports(Path.of("src/main.js"))) {
  DapSession session = manager.start(
      Path.of("src/main.js"),
      List.of(12, 20),
      new DapSession.Listener() {
        @Override
        public void onStopped(int threadId, String reason) {
          System.out.println("stopped: " + reason);
        }
      });
  // session.resume(), session.stepOver(), session.close()
}
```

Sessions always start the program with a `launch` request; `attach` is never
used. The adapter's `runInTerminal` request starts the debuggee with its standard
input, output and error streams connected to the I/O tab through the shared
`Input` and `Output` APIs. For an unknown platform, missing download, or failed
installation, `DapManager.DapUnavailableException` carries a user-facing message.
`MainFrame` shows that message and keeps debugging available for the other
languages.

`DapManager.start` refuses adapters whose transport is `jdtls`, because there is
no standalone process to launch; use `startJava` for those.

## Java through the JDT language server

The Java debug adapter is hosted by Eclipse JDT LS, so the IDE never downloads or
launches it. `JavaDebugBridge` uses the workspace commands the language server
exposes and then opens a `DapSession` over TCP on the returned port:

1. `vscode.java.startDebugSession` starts the debug server and returns its port.
2. `vscode.java.resolveMainClass` resolves the main class of the open file; the
   entry whose simple name matches the file name is preferred, otherwise the
   first one is used.
3. `vscode.java.resolveClasspath` returns `[modulePaths, classPaths]`, which are
   forwarded to the adapter.
4. The DAP `launch` request carries `mainClass`, `projectName`, `modulePaths`,
   `classPaths`, `cwd`, `name` and `console: internalConsole`.

```java
JavaDebugClient client = (command, arguments) -> lspClient.executeCommand(command, arguments);
DapSession session = manager.startJava(Path.of("src/App.java"), client, List.of(10), listener);
```

`MainFrame` reuses the language server already connected to the tab, connecting
one when the tab has none yet. When the language server fails a command, the
session is not started and the error message names the failing step.

### Requirements

Java debugging requires an Eclipse JDT language server that has the
`vscode-java-debug` plugin loaded; otherwise `vscode.java.startDebugSession` is
not registered and the IDE reports that the language server rejected the
command. Providing JDT LS and the plugin is a user environment requirement and is
not downloaded by the IDE, so the Java path is verified in the build only against
a scripted adapter and a stubbed language-server client (see `Tests`).

## Breakpoints

Breakpoints are stored per editor in `Editor` and painted in the gutter icon
column through `Gutter.addLineTrackingIcon`:

- Clicking any part of the gutter toggles the breakpoint of the clicked line.
  The breakpoint column is drawn by the gutter's icon row header, a child
  component, so `EditorTab` installs the mouse listeners on the gutter and on all
  of its children.
- The line under the pointer shows a translucent dot as a preview; a selected
  line shows a solid red dot.
- F9 toggles a breakpoint on the caret line.
- Every toggle notifies the listener registered with
  `Editor.setBreakpointListener(Consumer<List<Integer>>)`, which `MainFrame`
  wires to `DapSession.setBreakpoints`, so the adapter receives the current line
  set for the debugged file.

## Output and controls

Adapter and debuggee output is published to the shared `Output` queue, so the I/O
tab renders it with the same `[OUTPUT]`, `[ERROR]` and `[DEBUG]` prefixes as
normal program output. Session milestones are published as `[OUTPUT]` markers so
the user can follow the handshake:

| Marker | Published when |
| --- | --- |
| `[OUTPUT] {INITIALIZED}` | The adapter answered the DAP handshake with its `initialized` event |
| `[OUTPUT] {WAITING FOR STDIN}` | The debuggee is running and its input is open, once after launch and again after each forwarded line |
| `[OUTPUT] {FINISHED}` | The adapter reported `terminated`, or the session was closed by the user |

`DapSession` exposes `resume`, `pause`, `stepOver`, `stepInto`, `stepOut` and
`close`, which back the debug buttons shown by `IOPanel` while a debug session is
active. Stepping requests are only sent while the adapter reported a stopped
state; otherwise the I/O tab prints that execution is running.

## Tests

- `DapRegistryTest` covers registry parsing, platform/architecture matching,
  command placeholders and bundled downloads.
- `DapRegistryIntegrationTest` covers that every supported platform resolves to
  an adapter and that every adapter ships downloads for all OS/architecture
  combinations.
- `DapLanguageRegistryTest` covers the four debuggable languages, their
  transports, source-file language mapping and that language-server adapters are
  never installed as processes.
- `DapManagerTest` covers unsupported-platform fallback without network access.
- `DapInstallerTest` covers installed-version markers and cleanup after a rejected
  insecure download.
- `DapSessionIntegrationTest` starts a real adapter process over `stdio` and
  `tcp` and drives the full session: `initialize`, `launch`,
  `setBreakpoints`/`configurationDone`, `continue`, `next`, `stepIn`, `stepOut`,
  `pause`, breakpoint updates during a session, typed standard input reaching the
  live debuggee, and the `{INITIALIZED}`/`{WAITING FOR STDIN}`/`{FINISHED}`
  markers.
- `DapIOPanelIntegrationTest` clicks every debug button in the I/O tab against a
  live adapter and checks the adapter received the matching request.
- `JavaDebugBridgeIntegrationTest` runs the Java path end to end against a real
  TCP adapter and a stubbed language-server client, asserting the command
  sequence, the `mainClass`/`projectName` launch properties, breakpoints and
  execution controls.
- `JsDebugAdapterIntegrationTest` debugs a real Node.js program with the installed
  `js-debug` adapter: it stops at a breakpoint, steps over, resumes and checks the
  program output. The test is tagged `language-tools` and skipped when the current
  operating system and architecture have no registered adapter, when the adapter
  is not installed, or when `node` is missing, so `make test-nolang` and headless
  CI images without the external JavaScript toolchain still pass.
- `IOPanelTest` and `InputForwarderTest` cover output colouring, input forwarding
  and debug-mode controls.
