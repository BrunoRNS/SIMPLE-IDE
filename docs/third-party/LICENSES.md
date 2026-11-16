# License inventory

Simple IDE itself is licensed under the [BSD 3-Clause License](../../LICENSE).
This file lists every third-party component that reaches a user of the fat JAR
or a jpackage package, with the license that applies.

## Maven libraries compiled into the fat JAR

| Component | License | Upstream |
| --- | --- | --- |
| rsyntaxtextarea, languagesupport, autocomplete (com.fifesoft) | BSD 3-Clause | <https://github.com/bobbylight/RSyntaxTextArea> |
| flatlaf, flatlaf-extras (com.formdev) | Apache License 2.0 | <https://github.com/JFormDesigner/FlatLaf> |
| jackson-databind (com.fasterxml.jackson.core) | Apache License 2.0 | <https://github.com/FasterXML/jackson-databind> |
| jediterm-core, jediterm-ui (org.jetbrains.jediterm) | Apache License 2.0 | <https://github.com/JetBrains/jediterm> |
| pty4j (org.jetbrains.pty4j) | Apache License 2.0 | <https://github.com/JetBrains/pty4j> |
| jna (net.java.dev.jna) | Apache 2.0 / LGPL 2.1 dual | <https://github.com/java-native-access/jna> |
| org.eclipse.lsp4j | Eclipse Public License 2.0 | <https://github.com/eclipse-lsp4j/lsp4j> |
| jakarta.json-api | EDL 1.0 (BSD 3-Clause) | <https://github.com/jakartaee/json-api> |
| parsson (org.eclipse.parsson) | EDL 1.0 (BSD 3-Clause) | <https://github.com/eclipse-parsson/parsson> |
| jarchivelib (org.rauschig) | Apache License 2.0 | <https://github.com/thrau/jarchivelib> |
| groovy, groovy-jsr223 (org.apache.groovy) | Apache License 2.0 | <https://groovy-lang.org/> |
| kotlin-scripting-jsr223 (org.jetbrains.kotlin) | Apache License 2.0 | <https://kotlinlang.org/> |
| logback-classic (ch.qos.logback) | EPL 1.0 / LGPL 2.1 dual | <https://logback.qos.ch/> |

## Test-only dependencies (not in the fat JAR)

| Component | License |
| --- | --- |
| junit-jupiter-engine | EPL 2.0 |
| assertj-core | Apache License 2.0 |
| awaitility | Apache License 2.0 |

## Bundled resources

| Component | License | Notes |
| --- | --- | --- |
| BusyBox binaries (Linux, macOS) | GPLv2 | Source archives bundled in the same resource directory |
| busybox-w32 binaries (Windows) | GPLv2 | <https://frippery.org/busybox/> |

The per-platform `LICENSE.txt` files and the complete GPL source archives live
under `simpleide/src/main/resources/io/simpleide/ui/terminal/core/busybox/`.
Details: [Bundled BusyBox](BUSYBOX.md).

## Downloaded at runtime

| Component | License | Notes |
| --- | --- | --- |
| Language servers from `registry.json` | Their own upstream licenses | Downloaded into `~/.simpleide/lsp/` on first use; not redistributed by Simple IDE. Details: [Runtime downloads](RUNTIME.md) |

## Dual-license choices

- JNA is used under its Apache 2.0 license.
- Logback can be used under either EPL 1.0 or LGPL 2.1.

## External toolchains

`javac`, `kotlinc`, `groovy`, `node`, `python3`, `gcc`/`g++`/`clang`, `mono`,
`go` and `rustc` are programs you install separately. Their licenses are the
concern of your installation, not of this project.

See also: [Third-party overview](README.md) and
[Maven dependencies](MAVEN.md).
