# Third-party overview

Simple IDE is BSD 3-Clause, but it ships, downloads or depends on third-party
code. This section explains every category, where the code comes from and which
license applies.

## Categories

| How the code reaches you | What it is | License summary | Details |
| --- | --- | --- | --- |
| Declared in `pom.xml` (compiled into the fat JAR) | Runtime and test libraries | Apache 2.0, BSD 3-Clause, EPL 2.0, EDL 1.0, LGPL dual-licensed | [Maven dependencies](MAVEN.md) |
| Bundled inside the JAR resources | BusyBox binaries per platform, plus their source archives and license texts | GPLv2 | [Bundled BusyBox](BUSYBOX.md) |
| Downloaded at runtime by the LSP installer | Language servers, extracted into `~/.simpleide/lsp/` | Each server has its own license, recorded in the download registry | [Runtime downloads](RUNTIME.md) |
| External programs you install yourself | `javac`, `kotlinc`, `node`, `python3`, `gcc`, `mono`, `go`, `rustc` and other toolchains | Your own installation; Simple IDE never bundles them | [Runtime downloads](RUNTIME.md) |

## Why this matters

- **Redistribution.** If you distribute the fat JAR or a jpackage package, you
  must keep the bundled license files and BusyBox source archives intact.
- **Linking questions.** The BusyBox binaries are separate programs invoked as
  processes, not linked Java classes. The Maven libraries are ordinary Java
  dependencies; their licenses permit redistribution in a combined JAR.
- **Network behavior.** The only runtime downloads are LSP servers. Language
  toolchains and BusyBox are never downloaded at runtime.

## Where the license files live

- `LICENSE` — the BSD 3-Clause text of Simple IDE itself.
- `docs/third-party/LICENSES.md` — the complete inventory of all third-party
  components and their licenses.
- `simpleide/src/main/resources/io/simpleide/ui/terminal/core/busybox/` —
  per-platform `LICENSE.txt` files and BusyBox source archives.
- `simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json` — the
  LSP server registry, including the download source of each server.

## Quick reference for re-distributors

1. Keep `LICENSE` next to your redistribution.
2. Keep the BusyBox resources inside the JAR or package; do not strip the
   source archives or `LICENSE.txt` files.
3. Mention the licenses of the Maven libraries if your redistribution excludes
   them or replaces them.
4. If you redistribute an LSP server installation, follow the license of that
   server, not this document.
