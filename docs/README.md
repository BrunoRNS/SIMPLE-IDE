# Simple IDE documentation

This is the entry point for all project documentation. The documentation is
split by audience instead of being kept in one large file.

## Shortcuts

| I need to... | Read |
| --- | --- |
| Build, test or package the IDE | [Build and packaging](build/MAKEFILE.md) |
| Understand the release process and artifact names | [Releases](build/RELEASES.md) |
| Contribute a change | [Contributing](development/CONTRIBUTING.md) |
| Follow the branch model | [Branch model](development/BRANCHING.md) |
| Format code | [Code style](development/CODE_STYLE.md) |
| Understand the tests | [Testing](development/TESTING.md) |
| Embed the editor or another component | [API reference](api/README.md) |
| Use only RSyntaxTextArea | [Standalone editor](api/rsyntax.md) |
| Use only the thread system | [Threading API](api/threading.md) |
| Use only the language runner | [Languages API](api/languages.md) |
| Use only the LSP client | [LSP API](api/lsp.md) |
| Use the terminal | [Terminal API](api/terminal.md) |
| See the IDE in pictures | [Screenshots](screenshots/README.md) |
| Add or understand a language | [Languages reference](reference/LANGUAGES.md) |
| Understand language servers | [LSP reference](reference/LSP.md) |
| Understand thread management | [Thread reference](reference/THREADMASTER.md) |
| Know what is bundled or downloaded | [Third-party overview](third-party/README.md) |
| Read the license inventory | [Third-party licenses](third-party/LICENSES.md) |

## Documentation map

```text
docs/
├── README.md                     This index
├── api/                          How to import and use the public API
│   ├── README.md                 Facade overview and design rules
│   ├── getting-started.md        First integration
│   ├── artifacts.md              Release JAR, JitPack and manual installs
│   ├── isolated-modules.md       Using one subsystem only
│   ├── rsyntax.md                Standalone RSyntaxTextArea
│   ├── editor.md                 Embedded editor and search
│   ├── threading.md              ThreadManager facade
│   ├── languages.md              Language discovery and execution
│   ├── lsp.md                    Language Server Protocol client
│   ├── terminal.md               Embedded BusyBox terminal
│   ├── ui.md                     Popups, icons, themes and layouts
│   └── examples.md               Copy-paste examples
├── build/                        Build, package and release documentation
│   ├── MAKEFILE.md               Make targets and CI usage
│   └── RELEASES.md               Release automation and artifact names
├── development/                  Contributor documentation
│   ├── CONTRIBUTING.md           How to propose changes
│   ├── BRANCHING.md              Branch model
│   ├── CODE_STYLE.md             Formatting rules
│   └── TESTING.md                Test layout and tags
├── reference/                    Subsystem references
│   ├── LANGUAGES.md              Language support reference
│   ├── LSP.md                    Language Server Protocol reference
│   └── THREADMASTER.md           Thread manager reference
├── third-party/                  Dependencies, downloads and licenses
│   ├── README.md                 Overview of every category
│   ├── MAVEN.md                  pom.xml dependencies and licenses
│   ├── RUNTIME.md                Runtime downloads and external tools
│   ├── BUSYBOX.md                Bundled BusyBox binaries and sources
│   └── LICENSES.md               Complete license inventory
└── screenshots/                  UI screenshots with descriptions
```

## Documentation conventions

- Everything is written in English.
- Each file has one purpose and links to related files instead of repeating
  large sections.
- Code examples use the public API package `io.simpleide.api` when possible.
- Lower-level examples name the exact class and method they use.
- Commands are written for the repository root and use the Maven Wrapper or
  GNU Make.
- Version-specific behavior is called out explicitly.
- Tables are used for short reference data; procedures are written as numbered
  steps.
- Links are relative so the documentation works when read from a checkout or a
  release archive.

## Quick command reference

```sh
make build         # compile only
make test          # full test suite
make test-nolang   # tests without external language execution
make jar           # executable fat JAR
make pkg           # every release package for this OS/architecture
make pkg-verify    # build packages and validate the executables
make change-version 1.0.1  # change the project version (asks y/N)
make run           # launch the fat JAR
make format        # apply the standard formatter
make format-check  # verify formatting
make verify        # format check + tests + fat JAR
make clean         # remove build outputs
```
