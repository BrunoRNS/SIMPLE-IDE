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
   and refreshes release metadata when its cache expires. Successful metadata
   lookups are reused for six hours (failed lookups for two minutes), avoiding
   a blocking network check on every tab while still allowing installed servers
   to update. If no server matches or
   connection fails, the editor falls back to RSyntaxTextArea's local language
   support when available.
3. `LspInstaller` reuses an installation only when its `.installed` marker
   matches the selected version. Otherwise it installs via the configured
   package command or downloads and extracts the configured archive into
   `~/.simpleide/lsp/`. Optional post-install commands are then run and the
   marker is written. Version metadata/API failures fall back to the registry
   version; installation failures are reported as `FAILED`.
4. `LspClient` starts the server as a stdio subprocess, initializes the LSP
   session, and sends document changes and requests. Each server has a small
   dedicated daemon executor for LSP4J protocol work and a dedicated stderr
   reader, so blocking process I/O does not occupy shared `ThreadMaster` workers.
   Startup, process-exit handling, and initialization timeout remain managed
   through `ThreadMaster`; closing the server stops its executor and reader.
   UI updates are queued with `ThreadMaster.executeOnEDT`.
5. Closing an editor sends `didClose`; closing the workspace/server shuts down
   the LSP process, its executor, and its managed runtime nodes.

Initialization timeouts use the managed scheduler rather than the JVM
common-pool timeout facility. Language server processes are operating-system
processes, not Java threads.

### Local language-support fallback

When no language server matches the host or a connection fails, the editor
installs `LanguageSupportFactory` support for the file's syntax style and
removes it when a real LSP client connects or the tab closes. The bundled
`languagesupport` 3.4.1 library provides support for Java, JavaScript, Groovy,
C, CSS, HTML, JSP, Less, Perl, PHP, TypeScript, Unix shell, XML, and 6502
assembly. Of the file types the editor currently maps, this covers Java,
JavaScript, Groovy, and C. It has no provider for Python, C++, Kotlin, C#, Go,
or Rust; those files keep syntax highlighting only, and Python has no local
fallback. The fallback is not an LSP server and does not provide diagnostics,
go-to-definition, or formatting.

## Server coverage

The matrix below is the effective intersection of the registry and
`LangPlatform`'s host detection. `x86` means 32-bit Intel, `x64` means 64-bit
Intel, and `ARM64` covers `aarch64`/`arm64` registry names. A listed platform
means a server definition exists; it does not guarantee that its runtime is
installed or that the download/installation succeeds.

| Language | Server | Linux | macOS | Windows | Runtime/version requirement |
| --- | --- | --- | --- | --- | --- |
| Java | Eclipse JDT LS | x64, ARM64 | x64, ARM64 | x64, ARM64 | JDT LS selects the highest compatible entry requiring Java 17 or 21. Windows x86 uses local fallback support when available. |
| Kotlin | kotlin-language-server | x86, x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Java 11 or newer. |
| Groovy | groovy-language-server | x64, ARM64 | x64, ARM64 | — | Java 17 or newer; the registry builds the server from source after download. |
| JavaScript | typescript-language-server | x64, ARM64 | x64, ARM64 | x64, ARM64 | Node.js 22.22.2 or newer. Windows x86 uses local fallback support. |
| Python | Pyright | x64, ARM64 | x64, ARM64 | x64, ARM64 | Node.js 14 or newer. Windows x86 has no equivalent local language-support provider. |
| C# | csharp-ls | x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | .NET SDK 10 or newer. |
| C | clangd | x64, ARM64 | x64, ARM64 | x64 | The configured clangd release is installed from its platform archive. |
| C++ | clangd | x64, ARM64 | x64, ARM64 | x64 | Shares the clangd installation with C. |
| Go | gopls | x86, x64, ARM64 | x86, x64, ARM64 | x86, x64, ARM64 | Go 1.26 or newer. |
| Rust | rust-analyzer | x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Uses platform-specific release assets; Windows x86 uses the i686 MSVC archive. |

### Coverage notes

- The registry has no C/C++ server for Windows x86 or Windows ARM64, and no
  Groovy server for Windows.
- Java, JavaScript, and Python have no language-server entry for Windows x86.
- Some registry entries declare multiple equivalent architecture names.
  `LspServer.isSupportedOnCurrentPlatform()` normalizes x64/x86_64 and
  ARM64/aarch64 when matching the host.
- Platform coverage is independent of the application's compiler/interpreter
  setup: syntax highlighting can work even if a language server or its runtime
  is unavailable.
