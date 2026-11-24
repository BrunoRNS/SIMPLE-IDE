# Simple IDE documentation

This is the entry point for all project documentation. The documentation is
split by audience instead of being kept in one large file. Changes under
`docs/` are synced to the GitHub Wiki by CI.

## Shortcuts

| I need to... | Read |
| --- | --- |
| Build, test or package the IDE | [Build and packaging](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Makefile) |
| Understand the release process and artifact names | [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases) |
| Contribute a change | [Contributing](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Contributing) |
| Follow the branch model | [Branch model](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Branching) |
| Format code | [Code style](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Code-Style) |
| Understand the tests | [Testing](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Testing) |
| Embed the editor or another component | [API reference](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API) |
| Use only RSyntaxTextArea | [Standalone editor](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-RSyntax) |
| Use only the thread system | [Threading API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Threading) |
| Use only the language runner | [Languages API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Languages) |
| Use only the LSP client | [LSP API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-LSP) |
| Use the terminal | [Terminal API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Terminal) |
| Use the file tree, menus, popups or log console | [UI component guides](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-Application) |
| Use the standalone bytecode compiler | [Compiler guide](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Compiler) |
| Reuse file, resource and map helpers | [Shared utilities](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Utilities) |
| See the IDE in pictures | [Screenshots](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Screenshots) |
| Add or understand a language | [Languages reference](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Languages) |
| Understand language servers | [LSP reference](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-LSP) |
| Understand thread management | [Thread reference](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-ThreadMaster) |
| Choose or force a JavaFX media variant | [JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media) |
| Check whether a Linux distribution is supported | [Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux) |
| Check whether a Windows version is supported | [Windows compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows) |
| Check whether a macOS version is supported | [macOS compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-macOS) |
| Know what is bundled or downloaded | [Third-party overview](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party) |
| Read the license inventory | [Third-party licenses](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Licenses) |

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
│   ├── examples.md               Copy-paste examples
│   ├── ui/                       Application UI component guides
│   │   ├── application.md        Bootstrap, MainFrame and layout helpers
│   │   ├── logging.md            Debug console and Logback appender
│   │   ├── popups.md             Managed modal dialogs
│   │   ├── menubar.md            Menus and theme manager
│   │   └── file-tree.md          Project tree model and watcher
│   ├── compiler/                 Standalone bytecode compiler
│   │   └── README.md             Java, Kotlin and Groovy execution
│   └── util/
│       └── utilities.md          Files, resources, PATH lookup, map conversion
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
│   ├── JAVAFX-MEDIA.md           Optional media variants and Maven consumer contract
│   ├── LINUX.md                  Linux distributions, glibc and architecture support
│   ├── LSP.md                    Language Server Protocol reference
│   ├── MACOS.md                  macOS versions and JDK-dependent minimums
│   ├── THREADMASTER.md           Thread manager reference
│   └── WINDOWS.md                Windows versions and architecture support
├── third-party/                  Dependencies, downloads and licenses
│   ├── README.md                 Overview of every category
│   ├── MAVEN.md                  pom.xml dependencies and licenses
│   ├── RUNTIME.md                Runtime downloads and external tools
│   ├── BUSYBOX.md                Bundled BusyBox binaries and sources
│   └── LICENSES.md               Complete license inventory
└── screenshots/                  UI screenshots with descriptions
```

## GitHub Wiki publishing

The Markdown files in this directory are the source for the repository Wiki.
`../scripts/export_wiki.py` flattens these nested source files into `docs/wiki/`
using stable page names and converts internal Markdown links to Wiki links. The
flat export is required by the `victor-public/wiki-automation` Marketplace
action, which copies the contents of its configured folder into the Wiki.

The GitHub Actions workflow runs only when documentation changes are pushed to
`main`. It generates the export and syncs `docs/wiki/` to the Wiki. `_Sidebar.md`
and `_Footer.md` in that export configure Wiki navigation and its footer. Enable
the repository Wiki and create its first page once so the Wiki Git repository
exists. Allow the workflow's `GITHUB_TOKEN` to write repository contents; the
action needs access to the Wiki Git repository to publish pages.
Images remain in `docs/screenshots/` and are linked from Wiki pages using
repository-hosted URLs.

Edit the organized source files above, not the generated files in `docs/wiki/`;
the workflow regenerates the export before publishing. The root README links
directly to Wiki pages.

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
make format        # apply Spring Java Format
make check-format  # verify formatting
make verify        # format check + tests + fat JAR
make clean         # remove build outputs
```
