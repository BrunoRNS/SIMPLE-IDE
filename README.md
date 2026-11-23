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

Add the JitPack repository and depend on the published repository artifact:

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
  <version>v1.0.0beta</version>
</dependency>
```

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies {
    implementation 'com.github.BrunoRNS:SIMPLE-IDE:v1.0.0beta'
}
```

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

### Fat JAR / package (advanced users)

Download from [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/releases):

- `simpleide-v<version>-any-with-deps.jar` — platform-independent, run with
  `java -jar`.
- `simpleide-v<version>-<os>-<arch>.zip` — self-contained static package for
  Linux and Windows, no installation step.
- `simpleide-v<version>-linux-<arch>.deb` / `.rpm`,
  `simpleide-v<version>-macos-<arch>.dmg` and
  `simpleide-v<version>-windows-x64.msi` — native installers per platform. The
  Windows `.msi` is published for `x64` only; `x86` and `arm64` ship the zip.

Beta versions (e.g. `1.0.0beta`) are published as pre-releases.

Linux builds require a GNU/Linux distribution with **glibc 2.28 or newer**
(Debian 10+, Ubuntu 18.10+, Fedora 29+, RHEL 8+). Alpine Linux and other
musl-based distributions are not supported. See
[Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux).

Windows builds require **Windows 10** on `x86` and `x64`, and **Windows 11** on
`arm64`. macOS builds require **Big Sur (11.0)** on `x64` and `arm64`; the
release `.dmg` is built with JDK 17. Packages built with JDK 21, 25 and 27
require macOS 12, 14 and 15 respectively. See
[Windows compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows) and
[macOS compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-macOS).

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

The complete guides and API reference are published in the [SIMPLE-IDE Wiki](https://github.com/BrunoRNS/SIMPLE-IDE/wiki).

- [Documentation index](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Home)
- [API reference and integration guide](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API)
- [Languages](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Languages) ·
  [LSP](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-LSP) ·
  [Threading](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-ThreadMaster)
- [Linux](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux) · [Windows](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows) ·
  [macOS](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-macOS)
- [Build, test and packaging](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Makefile) ·
  [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases)
- [Third-party dependencies and licenses](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party)

## License

Simple IDE is licensed under the [BSD 3-Clause License](LICENSE). Bundled
components keep their own licenses — notably BusyBox (GPLv2, with source
archives bundled) and the Maven libraries listed in the
[license inventory](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Licenses).
