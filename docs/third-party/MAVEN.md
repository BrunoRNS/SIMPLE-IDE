# Maven dependencies

Every library compiled into the Simple IDE fat JAR is declared in a `pom.xml`.
Simple IDE is built with Maven; the wrapper (`mvnw`) downloads all of these
dependencies automatically, so no dependency is fetched at application runtime.

On 32-bit Linux only, if `mvnw` returns an error, the launcher downloads the
Apache Maven 3.9.16 binary archive with `wget`, extracts it under
`~/.m2/fallback/apache-maven-3.9.16`, adds its `bin` directory to `PATH`, and
retries the original Maven arguments with `mvn`. Other platforms continue to use
the Maven Wrapper without this fallback. The fallback requires `wget`, `tar`,
and Java on `PATH` (or `JAVA_HOME`).

## Runtime dependencies (simpleide module)

| Library | Version | License | Purpose |
| --- | --- | --- | --- |
| `com.twelvemonkeys.imageio:imageio-*` | 3.15.3 | BSD 3-Clause | ImageIO readers for additional raster, vector and document image formats |
| `org.apache.xmlgraphics:batik-*` | 1.19 | Apache 2.0 | SVG decoding for the TwelveMonkeys ImageIO Batik plugin |
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
| `com.github.hypfvieh:dbus-java-core` | 5.1.1 | MIT | Linux StatusNotifierItem tray integration on Wayland desktops |
| `com.github.hypfvieh:dbus-java-transport-jnr-unixsocket` | 5.1.1 | MIT | D-Bus session transport for Linux tray registration |
| `org.openjfx:javafx-controls/media/swing` | 22.0.2 on JDK 17–20, 23.0.2 on JDK 21+ | GPLv2 with the Classpath Exception | Optional JavaFX media playback embedded in Swing; consumer-selectable with `-Dsimpleide.media.variant` and resolved only on supported platforms |

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

The JavaFX dependencies are optional and consumer-selectable. Maven chooses
JavaFX 22 on JDK 17–20 and JavaFX 23 on JDK 21+ on Linux `x64`, Windows `x64`,
macOS `x64` and macOS `arm64`. Linux `arm64` always uses the `nojavafx` variant;
Linux `x86`, Windows `x86` and Windows `arm64` have no usable OpenJFX natives.
Consumers can force a variant with
`-Dsimpleide.media.variant=nojavafx|javafx22|javafx23`. In every other
configuration the fat JAR has no JavaFX classes and media files open in the hex
editor. The selection is implemented by the Maven profiles in
`simpleide/pom.xml`; `scripts/javafx-version.sh` mirrors it for `make`. See
[JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media).

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

- [Third-party overview](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party)
- [Bundled BusyBox](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-BusyBox)
- [Runtime downloads](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Runtime)
- [License inventory](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Licenses)
