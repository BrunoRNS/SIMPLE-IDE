# Runtime downloads and external tools

Simple IDE distinguishes three groups of external programs:

| Group | Bundled? | Downloaded by Simple IDE? | Example |
| --- | --- | --- | --- |
| Terminal shell (BusyBox) | Yes, in the JAR | Never | `busybox` |
| Language servers (LSP) | No | Yes, on first use | `jdtls` |
| Language toolchains | No | Never — you install them | `javac`, `python3` |

## LSP servers

Language servers are the **only** thing Simple IDE downloads at runtime. The
LSP installer reads
`simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json`, selects
the entry that matches the current OS, architecture and language, and installs
the server into:

```text
~/.simpleide/lsp/
```

Behavior summary:

- An installation is reused when the `.installed` marker file matches the
  selected version; otherwise the installer downloads and extracts the
  configured archive.
- Optional post-install commands are run after extraction.
- Successful release-metadata lookups are cached for six hours (failures for
  two minutes), so tab switches do not hit the network repeatedly.
- Servers known to the registry include `jdtls` (Java),
  `typescript-language-server` (JavaScript) and `csharp-ls` (C#); see the
  registry file for the complete list and the download source of each entry.
- Each server keeps the license of its own upstream project. Simple IDE does
  not redistribute server archives; it only downloads them on your machine.

## Language toolchains

Language execution relies on toolchains that you install yourself with your
operating system's package manager. Simple IDE never bundles or downloads them.

| Language | Tool detected | Notes |
| --- | --- | --- |
| Java | `javac`/`java`, or the embedded bytecode compiler | Java, Kotlin and Groovy also run through the embedded compiler module |
| Kotlin | `kotlinc` or the embedded compiler | |
| Groovy | `groovy` or the embedded compiler | |
| JavaScript | `node` | |
| Python | `python3` | |
| C / C++ | `gcc`/`g++` or `clang` | |
| C# | `mono` | |
| Go | `go` | |
| Rust | `rustc` | |

A language appears in the run menu only when its tool is found on `PATH`
(when using the embedded compiler, no external tool is required for that
language). See [Languages reference](../reference/LANGUAGES.md) for the exact
detection rules.

## Updating runtime metadata

The registry and its cache are documented in
[LSP reference](../reference/LSP.md). To change where a server is downloaded
from, edit `registry.json`; the installer reads it on the next start.

See also:

- [Third-party overview](README.md)
- [Bundled BusyBox](BUSYBOX.md)
- [License inventory](LICENSES.md)
