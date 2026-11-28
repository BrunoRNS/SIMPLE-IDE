# Language Server Protocol (LSP)

Simple IDE provides syntax highlighting independently of LSP. Language servers
add editor features such as diagnostics, completion, go-to-definition, document
formatting, and document synchronization. The supported server definitions and
their launch/install metadata live in
[`simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json`](../../simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json).

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
   support when available, then to a keyword-based completion provider for
   languages without a `LanguageSupport` implementation.
3. `LspInstaller` reuses an installation only when its `.installed` marker
   matches the selected version. Otherwise it installs via the configured
   package command or downloads and extracts the configured archive into
   `~/.simpleide/lsp/`. Optional post-install commands are then run and the
   marker is written. Version metadata/API failures fall back to the registry
   version; installation failures are reported as `FAILED`.
4.    `LspClient` starts the server as a stdio subprocess, initializes the LSP
   session (allowing up to 180 seconds for servers that index the workspace),
   and sends document changes and requests. Command arguments and the launch
   command support `${installDir}`, `${workspaceData}`, `${workspaceRoot}`,
   `${version}` and `${platformConfig}` placeholders. Each server has a small
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
JavaScript, Groovy, and C.

For the remaining editor-mapped languages (Python, C++, Kotlin, C#, Go, and
Rust), Simple IDE installs a lightweight `com.fifesoft:autocomplete` provider
containing language keywords and common built-ins. The same keyword provider is
used for every language when the JVM is headless. The fallback is not an LSP
server and does not provide diagnostics, go-to-definition, formatting, or
semantic completion.

## Server coverage

The matrix below is the effective intersection of the registry and
`LangPlatform`'s host detection. `x86` means 32-bit Intel, `x64` means 64-bit
Intel, and `ARM64` covers `aarch64`/`arm64` registry names. A listed platform
means a server definition exists; it does not guarantee that its runtime is
installed or that the download/installation succeeds.

| Language | Server | Linux | macOS | Windows | Runtime/version requirement |
| --- | --- | --- | --- | --- | --- |
| Java | Eclipse JDT LS | x64, ARM64 | x64, ARM64 | x64, ARM64 | JDT LS selects the highest compatible entry requiring Java 17 or 21. Windows x86 uses the local Java language-support fallback. |
| Kotlin | kotlin-language-server | x86, x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Java 11 or newer. Initialization may take longer than a minute while the server indexes the workspace; the client allows up to 180 seconds. |
| Groovy | groovy-language-server | x64, ARM64 | x64, ARM64 | — | Java 17 or newer; the registry builds the server from source after download with `gradlew -p <project directory>` and launches the resulting `*-all.jar`. |
| JavaScript | typescript-language-server | x64, ARM64 | x64, ARM64 | x64, ARM64 | Node.js 22.22.2 or newer. Windows x86 uses the local JavaScript language-support fallback. |
| Python | Pyright | x64, ARM64 | x64, ARM64 | x64, ARM64 | Node.js 14 or newer. Windows x86 uses keyword-based completion fallback. |
| C# | OmniSharp (Mono) | x86, x64, ARM64 | x86, x64, ARM64 | x86, x64, ARM64 | Mono 6 or newer with an MSBuild instance; works without a .NET SDK and analyzes loose `.cs` files in the workspace folder. |
| C | clangd | x64, ARM64 | x64, ARM64 | x64 | The configured clangd release is installed from its platform archive. |
| C++ | clangd | x64, ARM64 | x64, ARM64 | x64 | Shares the clangd installation with C. |
| Go | gopls | x86, x64, ARM64 | x86, x64, ARM64 | x86, x64, ARM64 | Go 1.26 or newer. Go 1.21 to 1.25 uses the pinned gopls 0.16.2 entry, which supports those toolchains. |
| Rust | rust-analyzer | x64, ARM64 | x64, ARM64 | x86, x64, ARM64 | Uses platform-specific release assets; Windows x86 uses the i686 MSVC archive. Needs the `rust-src` component (`rustup component add rust-src`) for standard-library completion. |

### Coverage notes

- The registry has no C/C++ server for Windows x86 or Windows ARM64, and no
  Groovy server for Windows.
- The C# server is the Mono build of OmniSharp. It never downloads or requires a
  .NET SDK; OmniSharp still needs an MSBuild instance (the `msbuild` command from
  Mono, or a .NET SDK when one is installed) to load loose files. The Debug CI
  workflow installs Mono from the Mono project repository, where
  `mono-complete` also provides `msbuild`.
- OmniSharp only analyzes files that belong to a project. When a C# folder has
  no `.sln`, `.csproj` or `.csx`, Simple IDE writes
  `SimpleIdeLooseFiles.csproj` next to the loose `.cs` files and includes them
  in it, so a single-file C# script gets completion. The file is not written
  when the folder already has a C# project.
- Java, JavaScript, and Python have no language-server entry for Windows x86.
  Java and JavaScript use their bundled local language support there, and Python
  uses keyword-based completion.
- JDT LS analyzes a loose `.java` file in the workspace folder even when no Maven
  or Gradle project exists: it answers completion and publishes syntax
  diagnostics for it. The IDE relies on this for editing and for debug
  (`JavaDebugBridge` compiles the file itself, because JDT reports no main class
  for a file outside a project). Both behaviors are covered by integration tests
  tagged `lsp` and `debug`.
- The Java keyword snippets (`psvm`, `main`, `sout`, `fori`, `scanner`) are only
  merged into a response that already contains server completions. When JDT LS
  answers nothing, the popup stays empty instead of showing only snippets, so a
  broken server is visible instead of hidden.
- Languages without bundled local language support receive keyword-based
  completion while an LSP is starting, when no server matches the host, or when
  a connection fails.
- Some registry entries declare multiple equivalent architecture names.
- rust-analyzer only discovers Cargo projects. When the workspace has no
  `Cargo.toml` or `rust-project.json`, each loose `.rs` file in the workspace
  folder is passed through the `linkedProjects` initialization option and
  analyzed as a standalone file, so single-file Rust scripts still get
  completion. Its standard-library completion also requires the `rust-src`
  component; without it the server answers every completion with `null` and the
  IDE logs a warning.
- The `lsp`-tagged Rust integration test skips when `rust-src` is absent. The
  Debug CI workflow installs a Rust toolchain with the `rust-src` component
  before `make test-lsp`.
- The Groovy server is built from its downloaded source. The Gradle wrapper runs
  with `-p <downloaded project directory>` because the server is installed next
  to, not inside, the extracted Gradle build.
- `LspServer.isSupportedOnCurrentPlatform()` normalizes x64/x86_64 and
  ARM64/aarch64 when matching the host.
- Platform coverage is independent of the application's compiler/interpreter
  setup: syntax highlighting can work even if a language server or its runtime
  is unavailable.
