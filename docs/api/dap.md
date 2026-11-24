# Debug Adapter Protocol (DAP)

`io.simpleide.dap` adds debug support to the Execute menu. It uses the Eclipse
LSP4J debug protocol client (`org.eclipse.lsp4j:org.eclipse.lsp4j.debug`) and the
Debug Adapter Protocol, so adapters are ordinary external processes that speak
DAP over standard input/output or TCP.

## Components

| Type | Responsibility |
| --- | --- |
| `DapRegistry` | Parses the bundled `registry.json` and matches adapters to the host platform |
| `DapAdapter` | Adapter command, arguments, transport, launch properties and downloads |
| `DapDownload` | Download URL and archive type for one OS/architecture pair |
| `DapInstaller` | Downloads, extracts and marks an adapter under `~/.simpleide/dap` |
| `DapManager` | Resolves, installs and starts a session for a source file |
| `DapSession` | Connects to the adapter, sends `initialize`/`launch` and exposes execution controls |

The registry lives at
`simpleide/src/main/resources/io/simpleide/dap/registry.json` (mirrored as
`dap_registry.json`). Each adapter has an id, name, version, launch command,
transport (`stdio` or `tcp`), optional launch properties and a download entry
per platform:

```json
{
  "adapters": [
    {
      "id": "js-debug",
      "name": "JavaScript Debug Adapter",
      "version": "1.140.1",
      "command": "node",
      "transport": "tcp",
      "launch": { "type": "node", "console": "internalConsole" },
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

Command arguments support `${installDir}`, `${program}` and `${port}`. Platform
matching uses `LangPlatform` values: `windows`, `linux` or `macos` combined with
`x86`, `x64`, `arm64` or `aarch64`; `amd64` is accepted as an alias of `x64`.

## Installing and launching

`DapManager.createDefault()` reads the bundled registry and detects the current
platform. `isSupported()` reports whether an adapter exists; `prepare()`
downloads and extracts it when no matching `.installed` marker is present.
Downloads must use HTTPS and are removed when extraction fails, so a failed
attempt never leaves a partial installation behind.

```java
DapManager manager = DapManager.createDefault();
if (manager.isSupported()) {
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
installation, `DapManager.DapUnavailableException` carries a user-facing message. `MainFrame`
then disables the Debug menu item and removes the Ctrl+F5 key binding until the
next successful attempt.

## Output and controls

Adapter output events are published to the shared `Output` queue, so the I/O tab
renders them with the same `[OUTPUT]`, `[ERROR]` and `[DEBUG]` colours as normal
program output. `DapSession` sends `setBreakpoints` for the selected file and
exposes `resume`, `pause`, `stepOver`, `stepInto`, `stepOut` and `close`, which
back the debug buttons shown by `IOPanel` while a debug session is active.

## Tests

- `DapRegistryTest` covers registry parsing, platform/architecture matching,
  command placeholders and bundled downloads.
- `DapRegistryIntegrationTest` covers that every supported platform resolves to
  an adapter and that every adapter ships downloads for all OS/architecture
  combinations.
- `DapManagerTest` covers unsupported-platform fallback without network access.
- `DapInstallerTest` covers installed-version markers and cleanup after a rejected
  insecure download.
- `IOPanelTest` and `InputForwarderTest` cover output colouring, input forwarding
  and debug-mode controls.
