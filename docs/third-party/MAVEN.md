# Maven dependencies

Every library compiled into the Simple IDE fat JAR is declared in a `pom.xml`.
Simple IDE is built with Maven; the wrapper (`mvnw`) downloads all of these
dependencies automatically, so no dependency is fetched at application runtime.

## Runtime dependencies (simpleide module)

| Library | Version | License | Purpose |
| --- | --- | --- | --- |
| `com.fifesoft:rsyntaxtextarea` | 3.6.3 | BSD 3-Clause | Syntax highlighting text editor |
| `com.fifesoft:languagesupport` | 3.4.1 | BSD 3-Clause | Local completion and code-folding support |
| `com.fifesoft:autocomplete` | 3.3.3 | BSD 3-Clause | Completion popup framework |
| `com.formdev:flatlaf` | 3.7.2 | Apache 2.0 | Flat look-and-feel themes |
| `com.formdev:flatlaf-extras` | 3.7.2 | Apache 2.0 | Extra FlatLaf features (SVG icons and helpers) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.4 | Apache 2.0 | JSON parsing (LSP registry and configuration files) |
| `org.jetbrains.jediterm:jediterm-core` | 3.76 | Apache 2.0 | Terminal emulation core |
| `org.jetbrains.jediterm:jediterm-ui` | 3.76 | Apache 2.0 | Swing terminal UI |
| `org.jetbrains.pty4j:pty4j` | 0.13.13 | Apache 2.0 | Pseudo-terminal allocation for the embedded terminal |
| `net.java.dev.jna:jna` | 5.14.0 | Apache 2.0 / LGPL 2.1 dual | Native access used by pty4j |
| `org.eclipse.lsp4j:org.eclipse.lsp4j` | 0.24.0 | EPL 2.0 | Language Server Protocol client bindings |
| `jakarta.json:jakarta.json-api` | 2.1.3 | EDL 1.0 (BSD 3-Clause) | Jakarta JSON API |
| `org.eclipse.parsson:parsson` | 1.1.7 | EDL 1.0 (BSD 3-Clause) | Jakarta JSON provider implementation |
| `org.rauschig:jarchivelib` | 1.2.0 | Apache 2.0 | Extraction of bundled archives (BusyBox, LSP servers) |

## Runtime dependencies (simplebytecode-compiler module)

| Library | Version | License | Purpose |
| --- | --- | --- | --- |
| `org.apache.groovy:groovy` | 6.0.0 | Apache 2.0 | Embedded Groovy compiler/runner |
| `org.apache.groovy:groovy-jsr223` | 6.0.0 | Apache 2.0 | JSR-223 scripting bridge for Groovy |
| `org.jetbrains.kotlin:kotlin-scripting-jsr223` | 2.4.21 | Apache 2.0 | Embedded Kotlin script runner |

## Logging and test dependencies

| Library | Version | License | Purpose |
| --- | --- | --- | --- |
| `ch.qos.logback:logback-classic` | 1.6.6 | EPL 1.0 / LGPL 2.1 dual | SLF4J logging implementation |
| `org.junit.jupiter:junit-jupiter-engine` | 5.14.4 | EPL 2.0 | Test framework |
| `org.assertj:assertj-core` | 4.0.0-M1 | Apache 2.0 | Test assertions |
| `org.awaitility:awaitility` | 4.2.2 | Apache 2.0 | Async test assertions |

Versions are re-checked against the `pom.xml` files when they change. Test
dependencies are never included in the fat JAR.

## Dual licenses

- JNA is dual-licensed Apache 2.0 / LGPL 2.1; the Apache 2.0 option is used by
  this distribution.
- Logback is dual-licensed EPL 1.0 / LGPL 2.1; either license can be honored
  when redistributing.

## Verifying the set

```sh
./mvnw -B dependency:tree | grep '::'
```

Run this from the repository root to list the resolved dependency graph of every
module. Compare the result with the tables above when you upgrade a version.

See also:

- [Third-party overview](README.md)
- [Bundled BusyBox](BUSYBOX.md)
- [Runtime downloads](RUNTIME.md)
- [License inventory](LICENSES.md)
