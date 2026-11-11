# Language Server Protocol (LSP)

Simple IDE provides syntax highlighting independently of LSP. Language servers
add editor features such as diagnostics, completion, go-to-definition, document
formatting, and document synchronization. The supported server definitions and
their launch/install metadata live in
[`simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json`](../simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json).

## Lifecycle

1. `EditorManager` maps the opened file's `TypeFile` to an LSP language ID and
   asks `LanguageServerManager` to connect. Servers are shared by language ID
   and workspace root. The root is the nearest ancestor containing a recognized
   build/workspace marker; otherwise the file's parent directory is used.
2. `LanguageServerManager` registers server startup with `ThreadMaster`.
   `LspManager` filters registry entries for the detected OS and architecture,
   selects the applicable server, checks any configured minimum runtime version,
   and refreshes release metadata where configured. If no server matches, the
   connection is skipped and its status returns to `IDLE`.
3. `LspInstaller` reuses an installation only when its `.installed` marker
   matches the selected version. Otherwise it installs via the configured
   package command or downloads and extracts the configured archive into
   `~/.simpleide/lsp/`. Optional post-install commands are then run and the
   marker is written. Version metadata/API failures fall back to the registry
   version; installation failures are reported as `FAILED`.
4. `LspClient` starts the server as a stdio subprocess, initializes the LSP
   session, and sends document changes and requests. The LSP4J executor is a
   `ThreadMaster`-backed executor attached beneath that server's runtime node.
   Startup, stderr reading, process-exit handling, initialization timeout, and
   executor work are therefore tracked and cancelled through `ThreadMaster`.
   UI updates are queued with `ThreadMaster.executeOnEDT`; direct EDT dispatch
   is confined to `ThreadMaster`'s internal scheduler and task lifecycle.
5. Closing an editor sends `didClose`; closing the workspace/server shuts down
   the LSP process, its executor, and its managed runtime nodes.

The managed worker pool is shared; each server gets a child executor whose
submitted tasks are represented as child nodes in the thread tree. Initialization
timeouts use the managed scheduler rather than the JVM common-pool timeout
facility. Language server processes are operating-system processes, not Java
threads.

## Server coverage

The matrix below is the effective intersection of the registry and
`LangPlatform`'s host detection. `x86` means 32-bit Intel, `x64` means 64-bit
Intel, and `ARM64` covers `aarch64`/`arm64` registry names. A listed platform
means a server definition exists; it does not guarantee that its runtime is
installed or that the download/installation succeeds.

| Language | Server | Linux | macOS | Windows | Runtime/version requirement |
| --- | --- | --- | --- | --- | --- |
| Java | Eclipse JDT LS; `jj-language-server` on Windows x86 | x64, ARM64 | x64, ARM64 | x86 (`jj-language-server`), x64, ARM64 | JDT LS selects the highest compatible entry requiring Java 17 or 21. Windows x86 uses Node/npm and strict npm engine checks. |
| Kotlin | kotlin-language-server | x86, x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Java 11 or newer. |
| Groovy | groovy-language-server | x64, ARM64 | x64, ARM64 | — | Java 17 or newer; the registry builds the server from source after download. |
| JavaScript | typescript-language-server | x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Node.js 22.22.2 or newer. |
| Python | Pyright | x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Node.js 14 or newer. |
| C# | csharp-ls | x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | .NET SDK 10 or newer. |
| C | clangd | x64, ARM64 | x64, ARM64 | x64 | The configured clangd release is installed from its platform archive. |
| C++ | clangd | x64, ARM64 | x64, ARM64 | x64 | Shares the clangd installation with C. |
| Go | gopls | x86, x64, ARM64 | x86, x64, ARM64 | x86, x64, ARM64 | Go 1.26 or newer. |
| Rust | rust-analyzer | x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Uses platform-specific release assets; Windows x86 uses the i686 MSVC archive. |

### Coverage notes

- The registry has no C/C++ server for Windows x86 or Windows ARM64, and no
  Groovy server for Windows.
- Java's Windows x86 entry is a separate Node-based server, not JDT LS. JDT LS
  entries cover x64 and ARM64 on Linux, macOS, and Windows.
- Some registry entries declare multiple equivalent architecture names.
  `LspServer.isSupportedOnCurrentPlatform()` normalizes x64/x86_64 and
  ARM64/aarch64 when matching the host.
- Platform coverage is independent of the application's compiler/interpreter
  setup: syntax highlighting can work even if a language server or its runtime
  is unavailable.
