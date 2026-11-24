# JavaFX media variants

Simple IDE can play audio and video in read-only editor tabs. The feature is
optional: the base application always builds and runs without JavaFX, and media
files fall back to the hex editor when playback is unavailable. This page is the
reference for the Maven architecture, the consumer contract and the platform
policy behind that behavior.

The public API is platform neutral:

- `io.simpleide.api.MediaPlayback` is the player interface.
- `io.simpleide.api.MediaPlaybacks` creates a player or reports playback as
  unavailable.
- `AudioViewer` and `VideoViewer` are the JavaFX implementations. They live in
  `simpleide/src/main/javafx/java` and are compiled only when a media variant
  enables JavaFX.

## Consumer contract (Maven)

Consumers select the media variant with a Maven user property:

```text
-Dsimpleide.media.variant=nojavafx|javafx22|javafx23
```

| Property value | Result |
| --- | --- |
| absent | Automatic selection from the consumer JDK and OS/architecture |
| `nojavafx` | No OpenJFX dependencies; media files use the hex editor |
| `javafx22` | OpenJFX 22.0.2 on a supported platform |
| `javafx23` | OpenJFX 23.0.2 on a supported platform |

The property is evaluated by the profiles in the published `simpleide` module
POM, so it controls the transitive dependencies that Maven resolves for a
JitPack, Maven Central or local-repository consumer. The module coordinate is:

```xml
<dependency>
  <groupId>com.github.BrunoRNS.SIMPLE-IDE</groupId>
  <artifactId>simpleide</artifactId>
  <version>v1.1.0</version>
</dependency>
```

Force a variant for one build:

```sh
# No JavaFX; media files open in the hex editor.
mvn -Dsimpleide.media.variant=nojavafx package

# Pin JavaFX 22 even on JDK 21+.
mvn -Dsimpleide.media.variant=javafx22 package

# Pin JavaFX 23.
mvn -Dsimpleide.media.variant=javafx23 package
```

Pin the variant for every build of a project in `.mvn/maven.config`:

```text
-Dsimpleide.media.variant=javafx23
```

`MAVEN_OPTS=-Dsimpleide.media.variant=javafx23` works too because Maven reads
system properties during profile activation.

> **Important:** Maven activates these profiles from user properties and system
> properties, not from `<properties>` declared in your own `pom.xml`. Put the
> value on the command line, in `.mvn/maven.config` or in `MAVEN_OPTS`.

### Automatic selection

When `simpleide.media.variant` is absent, the profiles choose a version:

| Consumer JDK | Supported platform | Selected variant |
| --- | --- | --- |
| 17 through 20 | Yes | `javafx22` (22.0.2) |
| 21 or newer | Yes | `javafx23` (23.0.2) |
| Any | No | `nojavafx` |

"Supported platform" means that OpenJFX publishes native artifacts and the
project enables them:

| Operating system | Architecture | Automatic variant |
| --- | --- | --- |
| Linux | `x64` (`amd64`) | `javafx22` or `javafx23` |
| Linux | `arm64` (`aarch64`) | `nojavafx` (always, by policy) |
| Linux | `x86` (32-bit) | `nojavafx` |
| macOS | `x64` (`x86_64`) | `javafx22` or `javafx23` |
| macOS | `arm64` (`aarch64`) | `javafx22` or `javafx23` |
| Windows | `x64` (`amd64`) | `javafx22` or `javafx23` |
| Windows | `x86` (32-bit) | `nojavafx` |
| Windows | `arm64` (`aarch64`) | `nojavafx` |

Forced variants on an unsupported platform still resolve to `nojavafx`. The
forced profiles require a matching OS/architecture, so no OpenJFX dependency is
added. In particular, `-Dsimpleide.media.variant=javafx23` on Linux `arm64`
never downloads OpenJFX.

### Why Linux arm64 is always `nojavafx`

Linux `arm64` is a first-class application platform, but the project policy is
to ship it without JavaFX. The OpenJFX `arm64` natives need a newer glibc than
the rest of the application, and the release baseline is deliberately kept at
glibc 2.28. Building and testing the whole `arm64` matrix without JavaFX keeps
that baseline honest. The rest of the IDE, including the hex editor, works
normally.

### Gradle

Gradle only evaluates a small subset of Maven profile activation from dependency
POMs (profiles active by default and profiles activated by the absence of a
system property). It does not evaluate JDK, OS or property-value activation.
Gradle consumers must therefore declare the OpenJFX modules explicitly:

```groovy
dependencies {
    implementation 'com.github.BrunoRNS.SIMPLE-IDE:simpleide:v1.1.0'

    // Add only the modules you want, with the platform classifier.
    def javafxVersion = '23.0.2'
    def javafxPlatform = 'linux' // linux, linux-aarch64, mac, mac-aarch64 or win
    implementation "org.openjfx:javafx-controls:${javafxVersion}:${javafxPlatform}"
    implementation "org.openjfx:javafx-media:${javafxVersion}:${javafxPlatform}"
    implementation "org.openjfx:javafx-swing:${javafxVersion}:${javafxPlatform}"
}
```

Omit the OpenJFX lines to get the `nojavafx` behavior. This caveat applies to
Gradle only; the Maven contract above is the supported automatic path.

### JitPack

JitPack builds the repository from a tag, branch or commit. The repository
contains `jitpack.yml`, which pins JDK 17:

```yaml
jdk:
  - openjdk17
install:
  - ./mvnw -B -DskipTests install
```

JDK 17 makes the published viewer classes compile against the JavaFX 22
baseline. The consumer still chooses the runtime variant with
`-Dsimpleide.media.variant`. If JitPack builds with a newer JDK, the automatic
variant in the consumer build is still calculated from the consumer's own JDK,
because the profiles live in the dependency POM.

Verify what a consumer actually resolves:

```sh
mvn -Dsimpleide.media.variant=javafx23 dependency:tree -Dincludes=org.openjfx
```

The tree must contain `javafx-controls`, `javafx-media` and `javafx-swing` at the
requested version and platform classifier. The repository contract test does
exactly this; see [Testing](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Testing#consumer-media-variant-tests).

## Maven profile architecture

The selection lives in `simpleide/pom.xml` and is built from small, composable
profiles instead of a single script-generated property. The version numbers are
declared once:

```xml
<simpleide.media.javafx22.version>22.0.2</simpleide.media.javafx22.version>
<simpleide.media.javafx23.version>23.0.2</simpleide.media.javafx23.version>
<javafx.version>${simpleide.media.javafx22.version}</javafx.version>
```

`javafx.version` is the value used by every dependency and is overridden by the
active profile. Profiles are grouped by purpose:

| Group | Activation | Responsibility |
| --- | --- | --- |
| `media-auto-jdk17` / `media-auto-jdk21` | JDK range, property absent | Choose 22 or 23 |
| `media-auto-platform-amd64` | `os.arch=amd64`, property absent | Linux/Windows x64 dependencies and source roots |
| `media-auto-platform-macos-x64` | macOS, `os.arch=x86_64`, property absent | macOS Intel dependencies and source roots |
| `media-auto-platform-macos-arm64` | macOS, `os.arch=aarch64`, property absent | macOS Apple Silicon dependencies and source roots |
| `media-force-javafx22-*` | property `javafx22` plus OS/arch | Pin 22 on that platform |
| `media-force-javafx23-*` | property `javafx23` plus OS/arch | Pin 23 on that platform |
| `media-force-nojavafx` | property `nojavafx` | No dependencies, no JavaFX source roots |

Each active platform profile does three things:

1. Adds `org.openjfx:javafx-controls`, `javafx-media` and `javafx-swing` with
   `${javafx.version}`. OpenJFX resolves the native classifier from the
   consumer's OS/architecture.
2. Adds `src/main/javafx/java` to the main source roots through
   `build-helper-maven-plugin`.
3. Adds `src/test/javafx/java` to the test source roots.

The `nojavafx` path adds neither dependencies nor source roots, so the compiled
JAR simply does not contain the viewer classes. The base `MediaPlaybacks` factory
is reflective and keeps compiling without them.

### Adding a platform

To enable a new platform:

1. Add an automatic platform profile (`media-auto-platform-<name>`) with the
   dependencies and source roots shown above.
2. Add matching forced profiles for `javafx22` and `javafx23`.
3. Update `scripts/javafx-version.sh` so `make` and the packaging scripts agree.
4. Update `scripts/ci/test-consumer-profiles.py` so the consumer contract test
   knows whether the platform is supported.
5. Update this page, [Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux) and the release notes.

## Runtime behavior

`MediaPlaybacks.isAvailable()` requires both an `AudioViewer` class and the
`javafx.scene.media.MediaPlayer` class. The second check matters when a consumer
forces `nojavafx` but the JAR still contains the viewer classes: files are then
classified as binary and open in the hex editor instead of failing when the
player is created.

The viewer classes are lazy. `MediaPlaybacks.audio(Path)` and
`MediaPlaybacks.video(Path)` return `null` when playback is unavailable. The
editor then opens `HexEditor`, and the log records why media support is absent.

JavaFX loads media asynchronously on its own application thread. The Swing UI is
never blocked by a large file. `close()` waits for the JavaFX thread to dispose
the player and detaches the scene, which releases the file handle on Windows.
Loading and codec errors are shown on the player status label instead of failing
silently.

## Build and packaging

`make` selects the variant through `scripts/javafx-version.sh` and forwards it to
Maven as `-Dsimpleide.media.variant=<variant>`.

| Environment variable | Meaning |
| --- | --- |
| `SIMPLEIDE_MEDIA_VARIANT` | `auto`, `nojavafx`, `javafx22` or `javafx23` |
| `SIMPLEIDE_JAVAFX_DISABLE` | `1` is a legacy alias for `SIMPLEIDE_MEDIA_VARIANT=nojavafx` |
| `SIMPLEIDE_JAVAFX_VERSION` | Legacy version override; `22.*` maps to `javafx22`, `23.*` to `javafx23`, `none` to `nojavafx` |

Examples:

```sh
make test                                  # automatic
SIMPLEIDE_MEDIA_VARIANT=nojavafx make jar  # no JavaFX
SIMPLEIDE_MEDIA_VARIANT=javafx23 make pkg  # pin JavaFX 23
```

Release packages carry the variant in the file name:

```text
simpleide-v1.1.0-linux-x64-javafx23.deb
simpleide-v1.1.0-linux-x86-nojavafx.deb
simpleide-v1.1.0-windows-x64-javafx22.msi
```

See [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases) for the full matrix and
[Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux#audio-and-video-playback) for the runtime
libraries.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `NoClassDefFoundError: javafx/...` at runtime | The consumer forced `javafx22`/`javafx23` but the platform is unsupported, so no OpenJFX was resolved | Use the automatic variant on a supported platform, or run with `nojavafx` |
| `dependency:tree` has no `org.openjfx` entries | Property absent on an unsupported platform, or `nojavafx` was forced | Check `java -XshowSettings:properties -version` for `os.arch` and the JDK version |
| `-Dsimpleide.media.variant` seems ignored | It was declared in the consumer POM `<properties>` | Pass it on the command line, in `.mvn/maven.config` or in `MAVEN_OPTS` |
| Media opens in the hex editor | No JavaFX classes, headless session, or unsupported platform | Install the JavaFX package variant or use a supported platform |
| Video plays without audio on Linux | ALSA/PulseAudio configuration missing | See [Audio output](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux#audio-output) |
| Unsupported codec | The system FFmpeg libraries are missing or too old | Install FFmpeg; JavaFX 23 accepts newer sonames than JavaFX 22 |

## Related documentation

- [Embedded editor and media API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Editor#audio-and-video)
- [Artifacts and imports](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Artifacts#optional-javafx-media-variant)
- [Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux#audio-and-video-playback)
- [Testing](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Testing#consumer-media-variant-tests)
- [Maven dependencies](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Maven)
