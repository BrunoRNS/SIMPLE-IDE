<h1 align="center" style="font-size:36;">SIMPLE-IDE</h1>

<p align="center"><img src="assets/simpleide_icon.png" alt="SIMPLE-IDE app icon" width="180" /></p>

A simple, lightweight, extensible IDE written in Java Swing — usable as a
standalone editor or as a component toolkit for building bigger, more specific
IDEs.

[![License: BSD 3-Clause](https://img.shields.io/badge/license-BSD%203--Clause-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/java-17%2B-orange.svg)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/build-maven-c71a36.svg)](https://maven.apache.org/)
![Platforms](https://img.shields.io/badge/platforms-Linux%20%7C%20macOS%20%7C%20Windows-lightgrey.svg)
[![Release](https://img.shields.io/github/v/release/BrunoRNS/SIMPLE-IDE?include_prereleases)](https://github.com/BrunoRNS/SIMPLE-IDE/releases)
[![Linux x86 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/linux32.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/linux32.yml)
[![Linux x64 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/linux64.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/linux64.yml)
[![Linux ARM64 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/linuxarm.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/linuxarm.yml)
[![Windows x86 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/windows32.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/windows32.yml)
[![Windows x64 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/windows64.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/windows64.yml)
[![Windows ARM64 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/windowsarm.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/windowsarm.yml)
[![macOS x64 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/macos-intel.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/macos-intel.yml)
[![macOS ARM64 CI](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/macos-arm.yml/badge.svg?branch=main)](https://github.com/BrunoRNS/SIMPLE-IDE/actions/workflows/macos-arm.yml)

## Screenshots

| | |
| --- | --- |
| ![Home page, default dark theme](docs/screenshots/simpleide-homepage-default-dark-v1.0.0beta.png) | ![Autocomplete, light theme, Java](docs/screenshots/simpleide-lighttheme-autocomplete-javalang-v1.0.0beta.png) |
| ![Embedded terminal, Dracula theme](docs/screenshots/simpleide-draculatheme-terminal-v1.0.0beta.png) | ![Opened editor, dark theme](docs/screenshots/simpleide-nolangfileopenededitor-dark-v1.0.0beta.png) |

More pictures and what each one shows: [Screenshots showcase](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Screenshots).

## Where it fits

Simple IDE is part of a planned architecture meant to be **integrated into
larger, more specific IDEs**: every subsystem (editor, terminal, threading,
language runner, LSP client) can be embedded independently through the public
API in [`io.simpleide.api`](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API). It also works perfectly well
**on its own** as a complete desktop editor.

- Standalone: a fast, small editor with run support for 10 languages and a
  terminal that needs no BusyBox installation.
- Embedded: grab `Editors`, `SimpleEditor`, `Threads`, `Languages`, `Terminals`
  or `SimpleIDE` and drop them into your own Swing application.

## Choosing how to use it

| You are | Path | Jump to |
| --- | --- | --- |
| Most users — add it as a library | Maven or Gradle dependency | [Maven / Gradle](#maven--gradle-recommended) |
| Advanced user — run the packaged app | Release JAR or package | [Fat JAR / package](#fat-jar--package-advanced-users) |
| Super advanced user / developer | Build from source | [From source](#from-source-super-advanced-users--developers) |

### Maven / Gradle (recommended)

Add the JitPack repository and depend on the published repository artifact.
The dependency below uses automatic variant selection:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.BrunoRNS</groupId>
  <artifactId>SIMPLE-IDE</artifactId>
  <version>v1.1.0</version>
</dependency>
```

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies {
    implementation 'com.github.BrunoRNS:SIMPLE-IDE:v1.1.0'
}
```

To pin a variant **directly in `pom.xml`, with no command-line flag**, exclude the
transitive OpenJFX modules and declare them explicitly. Use the version
`22.0.2` for `javafx22`, `23.0.2` for `javafx23` (JDK 21+), or omit the OpenJFX
dependencies entirely for `nojavafx`:

```xml
<properties>
  <javafx.version>23.0.2</javafx.version><!-- 22.0.2 for javafx22 -->
  <javafx.platform>linux</javafx.platform><!-- linux-aarch64, mac, mac-aarch64 or win -->
</properties>

<dependency>
  <groupId>com.github.BrunoRNS</groupId>
  <artifactId>SIMPLE-IDE</artifactId>
  <version>v1.1.0</version>
  <exclusions>
    <exclusion>
      <groupId>org.openjfx</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
</dependency>

<!-- Only for javafx22/javafx23; omit all three for nojavafx. -->
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-controls</artifactId>
  <version>${javafx.version}</version>
  <classifier>${javafx.platform}</classifier>
</dependency>
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-media</artifactId>
  <version>${javafx.version}</version>
  <classifier>${javafx.platform}</classifier>
</dependency>
<dependency>
  <groupId>org.openjfx</groupId>
  <artifactId>javafx-swing</artifactId>
  <version>${javafx.version}</version>
  <classifier>${javafx.platform}</classifier>
</dependency>
```

The wildcard exclusion needs Maven 3.2.1+. Gradle uses the same idea: exclude
the transitive OpenJFX modules, then omit the extra lines for `nojavafx` or pin
`22.0.2` / `23.0.2`:

```groovy
def javafxVariant = 'javafx23' // nojavafx | javafx22 | javafx23
def javafxVersion = javafxVariant == 'javafx23' ? '23.0.2' : '22.0.2'
def javafxPlatform = 'linux' // linux-aarch64, mac, mac-aarch64 or win

dependencies {
    implementation('com.github.BrunoRNS:SIMPLE-IDE:v1.1.0') {
        exclude group: 'org.openjfx'
    }
    if (javafxVariant != 'nojavafx') {
        implementation "org.openjfx:javafx-controls:${javafxVersion}:${javafxPlatform}"
        implementation "org.openjfx:javafx-media:${javafxVersion}:${javafxPlatform}"
        implementation "org.openjfx:javafx-swing:${javafxVersion}:${javafxPlatform}"
    }
}
```

Putting `simpleide.media.variant` in your POM `<properties>` does **not** select
a variant. The advanced property route (`-Dsimpleide.media.variant=...`,
`.mvn/maven.config` or `MAVEN_OPTS`) is documented in
[JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media).

Then embed any component:

```java
// A full editor tab with search, line numbers and lazy LSP:
SimpleEditor editor = SimpleEditor.open(Path.of("Main.java"));
frame.add(editor.component());

// Or the whole IDE:
SimpleIDE.launch();
```

Prefer no repository setup? Download the release JAR and install it locally —
see [Artifacts and imports](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Artifacts). To consume only the
compiler module use the `simplebytecode-compiler` artifact.

### Optional audio and video (JavaFX)

Audio and video playback is an optional build variant. Without it, media files
open in the built-in hex editor and the rest of the IDE is unchanged. The basic
dependency above selects the variant automatically: JavaFX 23 on JDK 21+,
JavaFX 22 on JDK 17–20, on a supported platform; `nojavafx` elsewhere. To pin
`nojavafx`, `javafx22` or `javafx23`, use the POM-only snippet above.

Supported platforms are Linux x64, Linux arm64 (glibc 2.34+ / Debian 12+ when
JavaFX is selected), Windows x64, macOS x64 and macOS arm64. Linux x86, Windows
x86 and Windows arm64 always use `nojavafx`. Automatic Linux arm64 builds also
use `nojavafx` so that the glibc 2.28 baseline is preserved; select `javafx22` or
`javafx23` there only on Debian 12+. Selections on unsupported platforms still
resolve to `nojavafx`.

Builds from source use `SIMPLEIDE_MEDIA_VARIANT=auto|nojavafx|javafx22|javafx23`
with `make`, for example `SIMPLEIDE_MEDIA_VARIANT=javafx23 make jar`.

Full contract, platform matrix, the advanced `-Dsimpleide.media.variant` route,
JitPack behavior and troubleshooting:
[JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media).

### Fat JAR / package (advanced users)

Download from [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/releases):

- `simpleide-v<version>-<os>-<arch>-<variant>-with-deps.jar` — executable fat JAR
  built for a system and media variant, run with `java -jar`.
- `simpleide-v<version>-<os>-<arch>-<variant>.zip` — self-contained static
  package for Linux and Windows, no installation step.
- `simpleide-v<version>-linux-<arch>-<variant>.deb` / `.rpm`,
  `simpleide-v<version>-macos-<arch>-<variant>.dmg` and
  `simpleide-v<version>-windows-x64-<variant>.msi` — native installers per
  platform. The Windows `.msi` is published for `x64` only; `x86` and `arm64`
  ship the zip. `<variant>` is `javafx22`, `javafx23` or `nojavafx`, and selects
  the JavaFX media build (or the hex-editor fallback) shipped in the package.

Version `1.1.0` is the current release. Versions containing `beta` are published as pre-releases.

Linux builds require a GNU/Linux distribution with **glibc 2.28 or newer**
(Debian 10+, Ubuntu 18.10+, Fedora 29+, RHEL 8+). Alpine Linux and other
musl-based distributions are not supported. Audio and video playback is
optional and additionally needs GTK 3, ALSA or PulseAudio and system FFmpeg.
The JavaFX `arm64` binaries need **glibc 2.34+ (Debian 12+)**; the `nojavafx`
`arm64` packages keep the Debian 10 baseline. All three variants are available on
Linux `x64`/`arm64`, macOS `x64`/`arm64` and Windows `x64`; Linux `x86`, Windows
`x86` and Windows `arm64` only receive `nojavafx`. On x64 (also called `amd64`), the
JavaFX natives need only glibc 2.17, so every variant runs on the baseline.

#### Compatibility map (summary)

| System | Published variants, best first | Minimum system |
| --- | --- | --- |
| Linux `x64` / `amd64` | `javafx23` → `javafx22` → `nojavafx` | glibc 2.28 (Debian 10+) |
| Linux `arm64` | `javafx23` → `javafx22` → `nojavafx` | glibc 2.34 (Debian 12+) for JavaFX; glibc 2.28 (Debian 10+) for `nojavafx`. `javafx23` is the JDK 21 build. |
| Linux `x86` | `nojavafx` | glibc 2.28 (Debian 10+) |
| macOS `x64` / `arm64` | `javafx23` → `javafx22` → `nojavafx` | macOS 12 for `javafx23`; macOS 11 (Big Sur) for `javafx22` and `nojavafx` |
| Windows `x64` | `javafx23` → `javafx22` → `nojavafx` | Windows 10 |
| Windows `x86` / `arm64` | `nojavafx` | Windows 10 / Windows 11 |

Windows builds require **Windows 10** on `x86` and `x64`, and **Windows 11** on
`arm64`. macOS builds require **Big Sur (11.0)** on `x64` and `arm64`; the
`javafx22` release `.dmg` is built with JDK 17, while `javafx23` is built with
JDK 21 and requires macOS 12. See
[Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux),
[JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media),
[Windows compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows) and
[macOS compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-macOS).

#### Download the best package with a script

`scripts/install-simpleide.sh` (Linux/macOS) and
`scripts/install-simpleide.ps1` (Windows) validate the system, find the latest
GitHub release and download the most advanced published variant:
`javafx23`, then `javafx22`, then `nojavafx`. The file is left in the current
directory; the scripts do **not** extract, install or run it.

```sh
bash scripts/install-simpleide.sh
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-simpleide.ps1
```

Options, overrides (`SIMPLEIDE_TAG`, `SIMPLEIDE_OUTPUT_DIR`, `SIMPLEIDE_FORCE`)
and troubleshooting: [Script installation](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Install).

### From source (super advanced users / developers)

Requirements: JDK 17+, GNU Make (optional). Build, test and run:

```sh
make jar          # executable fat JAR -> simpleide/target/simpleide.jar
make run          # launch it
make pkg          # jpackage app image for this OS/arch
```

Without Make, use the wrapper (`mvnw.cmd` on Windows): `./mvnw -DskipTests
package`. Development workflow, branch model and tests:
[Contributing](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Contributing).

## Features

- [Run I/O tab, Execute menu and DAP debugging](docs/features/io-tab.md).
- [Git Source Control side panel](docs/features/git-sidepanel.md).
- [Separate audio and video viewers](docs/features/mediaviewer.md).
- RSyntaxTextArea editor: highlighting, folding, auto-indent, search/replace,
  local completion.
- Optional LSP: diagnostics, completion, go-to-definition, formatting —
  servers are downloaded on demand, editors degrade gracefully without them.
- Run Java, Kotlin, Groovy, JavaScript, Python, C, C++, C#, Go and Rust;
  input/output routed through managed processes.
- Embedded BusyBox terminal per platform, no runtime download.
- FlatLaf themes, project tree with file watching, `ThreadMaster` task
  management.
- Source Control sidebar bound to the folder open in Explorer, with repository
  status, branch operations and a JGit commit graph.

## Team

Maintainers:

| Maintainer | GitHub | Contact |
| --- | --- | --- |
| Bruno R. N. S. | [@BrunoRNS](https://github.com/BrunoRNS) | <brunorns05@outlook.com> |
| Luis Otávio | [@LuisOtavio13](https://github.com/LuisOtavio13) & [@luisotavio15](https://github.com/luisotavio15) | <roberdoogarcia@gmail.com> |

Everyone else is welcome as a **collaborator**: open an issue or a pull
request and you are in. See [Contributing](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Contributing)
for the process.

## Documentation

The complete guides and API reference are maintained under [`docs/`](docs/README.md) and published to the [SIMPLE-IDE Wiki](https://github.com/BrunoRNS/SIMPLE-IDE/wiki).

- [Documentation index](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Home)
- [API reference and integration guide](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API)
- [Languages](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Languages) ·
  [LSP](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-LSP) ·
  [Threading](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-ThreadMaster)
- [Linux](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux) · [Windows](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows) ·
  [macOS](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-macOS) ·
  [JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media) ·
  [Script installation](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Install)
- [Build, test and packaging](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Makefile) ·
  [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases)
- [Third-party dependencies and licenses](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party)

## License

Simple IDE is licensed under the [BSD 3-Clause License](LICENSE). Bundled
components keep their own licenses — notably BusyBox (GPLv2, with source
archives bundled) and the Maven libraries listed in the
[license inventory](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Licenses).
