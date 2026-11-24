# Build and test with Make

The `Makefile` is a thin wrapper over the Maven Wrapper. It provides predictable
target names and keeps the fat JAR and package paths in one place.

## Prerequisites

- JDK 17 or newer. CI covers 17, 21, 25 and 27.
- On Linux, GNU glibc 2.28 or newer (Debian 10+, Ubuntu 18.10+, Fedora 29+,
  RHEL 8+). Alpine and other musl-based distributions cannot run the bundled
  JDK and native libraries. See [Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux).
- GNU Make on Linux and macOS. On Windows, use Git Bash, MSYS2 or the
  `mvnw.cmd` commands directly.
- `jpackage` from the JDK for `make pkg`.
- `zip` on Linux and macOS for the portable app image. PowerShell's
  `Compress-Archive` is used on Windows.
- External language toolchains for the full test suite. See
  [Runtime tools](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-Runtime).
- Optional audio/video support on Linux: GTK 3, ALSA and FFmpeg. `make` selects
  JavaFX 22 on JDK 17–20 and JavaFX 23 on JDK 21+ when the platform glibc
  supports it; unsupported platforms build without JavaFX and open media files
  in the hex editor. See
  [Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux#audio-and-video-playback).

Check the environment:

```sh
java -version
./mvnw -version
make help
```

## Targets

| Target | What it does | Produces |
| --- | --- | --- |
| `make` / `make all` | `test` then `jar` | Test reports and fat JAR |
| `make build` | Compile all modules, no tests, no packages | `target/classes` |
| `make test` | Full test suite | Surefire reports |
| `make test-nolang` | Tests without the `language-tools` tag | Surefire reports |
| `make jar` | Package the executable fat JAR, skip tests | `simpleide/target/simpleide.jar` |
| `make pkg` | `jar` plus every package type for the current OS/arch (Linux: zip + deb + rpm; macOS: dmg; Windows: zip, plus msi on x64 only). Artifacts carry the `javafx22`, `javafx23` or `nojavafx` variant suffix | `simpleide/target/pkg/` |
| `make pkg-verify` | `pkg` plus validation of every produced package | Exit status |
| `make change-version VERSAO` | Validate and change the project version after confirmation | Updated pom and properties files |
| `make run` | `jar` then launch the fat JAR | A running IDE |
| `make format` | Apply Spring Java Format | Formatted Java files |
| `make check-format` | Verify formatting | Exit status |
| `make format-check` | Alias for `check-format` | Exit status |
| `make verify` | `check-format`, `test`, `jar` | Validated JAR |
| `make clean` | Maven clean and remove package output | Clean tree |
| `make help` | Print targets, version and paths | Text |

## Variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `MVNW` | `./mvnw` (`mvnw.cmd` on Windows) | Maven command |
| `PKG_RUNNER` | `sh scripts/package.sh` (`package.ps1` on Windows) | Packaging command |
| `PKG_VERIFY` | `sh scripts/validate-package.sh` (`validate-package.ps1` on Windows) | Package validation command |
| `APP_JAR` | `simpleide/target/simpleide.jar` | Fat JAR path |
| `PKG_DIR` | `simpleide/target/pkg` | Package output |
| `VERSION` | Read from `simpleide.properties` | Release version |
| `JAVA_OPTS` | `-Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED` | Runtime options for `make run` |

On 32-bit Linux, if the wrapper exits with an error, `mvnw` automatically
downloads Apache Maven 3.9.16 with `wget`, extracts it into the user's Maven
cache, prepends its `bin` directory to `PATH`, and retries the same command.
This fallback is not used on other platforms.

Packaging environment variables:

| Variable | Values | Meaning |
| --- | --- | --- |
| `SIMPLEIDE_PKG_OS` | `linux`, `macos`, `windows` | Override OS detection |
| `SIMPLEIDE_PKG_ARCH` | `x86`, `x64`, `arm64` | Override architecture detection |
| `SIMPLEIDE_PKG_TYPE` | `app-image`, `deb`, `rpm`, `dmg`, `msi` (x64 only) | Build only one package type instead of the OS default set |

## Examples

Compile only:

```sh
make build
```

Run tests without external toolchains:

```sh
make test-nolang
```

Build the executable JAR and run it:

```sh
make jar
java -jar simpleide/target/simpleide.jar
```

Build every package for this OS (Linux: zip + deb + rpm; macOS: dmg;
Windows: zip, plus the msi on x64) and validate them:

```sh
make pkg-verify
```

Packaging overrides jpackage's default `--strip-native-commands` jlink option so
the bundled runtime keeps `java`, `javac` and `jdk.compiler`; the embedded
bytecode compiler and the Java language server then work without an external
JDK. The `.msi` installer is only built on `windows-x64`; `windows-x86` and
`windows-arm64` produce the static zip only. Windows x86 builds still pass
`-J-Dos.arch=x86` so jpackage derives its metadata from the 32-bit runtime
rather than the host `os.arch`. The package scripts pass the native app icon to
`jpackage` for every build: a 256-pixel PNG for Linux, a multi-size ICO for
Windows and an ICNS image for macOS. The native files are proportionally resized
from the bundled `simpleide_icon.png`.

Build only one package type:

```sh
SIMPLEIDE_PKG_TYPE=deb make pkg
```

Change the project version:

```sh
make change-version 1.0.1
```

The target validates the version (semantic version, optionally followed by
`beta`, `alpha` or `rc<number>`), lists every file that would change
(`pom.xml`, the module `pom.xml` files and `simpleide.properties`) and asks for
confirmation. The default answer is `N`, so an accidental run never changes
anything. The release workflow reads the version from `simpleide.properties`,
so `make change-version` followed by a `v<version>` tag is enough to publish a
new release.

Force a package name for a cross-run (the toolchain must still support it):

```sh
SIMPLEIDE_PKG_OS=linux SIMPLEIDE_PKG_ARCH=arm64 make pkg
```

## Maven without Make

The Make targets map directly:

| Make | Maven |
| --- | --- |
| `make build` | `./mvnw -B -DskipTests compile` |
| `make test` | `./mvnw -B test` |
| `make test-nolang` | `./mvnw -B -DexcludedGroups=language-tools test` |
| `make jar` | `./mvnw -B -DskipTests package` |
| `make format` | `./mvnw -B spring-javaformat:apply` |
| `make check-format` | `./mvnw -B spring-javaformat:validate` |
| `make clean` | `./mvnw -B clean` |

`make pkg` additionally runs the packaging script; there is no single Maven
lifecycle equivalent.

## Module layout

| Module | Artifact | Purpose |
| --- | --- | --- |
| `simplebytecode-compiler` | `simplebytecode-compiler` | Java, Kotlin and Groovy in-memory compilation and execution |
| `simpleide` | `simpleide` | Swing application, editor, LSP, terminal, language runners |

Build one module:

```sh
./mvnw -pl simpleide -am test
./mvnw -pl simplebytecode-compiler -am test
```

`-am` builds required reactor modules.

## Spring Java Format

Spring Java Format runs during `validate`, so `make build` and `make test` fail
when Java sources are not formatted. Use:

```sh
make format
```

`make check-format` verifies formatting without changing files, and
`make format-check` is kept as an alias. The formatter uses the Java 17 baseline
declared in `.springjavaformatconfig`, tab indentation and a 120-column line
limit. Do not disable the check. See
[Code style](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Development-Code-Style).

## Native access

The parent POM and Surefire pass:

```text
--enable-native-access=ALL-UNNAMED
```

This is required by JNA/pty4j on newer JDKs. `make run` adds the same option
through `JAVA_OPTS`. If you run the JAR yourself, add it to avoid native-access
warnings or failures:

```sh
java --enable-native-access=ALL-UNNAMED -jar simpleide/target/simpleide.jar
```

## Clean

```sh
make clean
```

Removes Maven output and `simpleide/target/pkg`. It does not remove the Maven
wrapper, downloaded dependencies, LSP installations under `~/.simpleide` or
language server caches under `~/.cache/simpleide`.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `mvnw: Permission denied` | Wrapper not executable | `chmod +x mvnw` |
| Format validation failure | Unformatted Java | `make format` |
| `jpackage: command not found` | JDK on `PATH` is a JRE or old JDK | Use JDK 17+ and check `jpackage --version` |
| `zip: command not found` | `zip` missing on Linux | Install `zip` |
| Tests fail on a language | Toolchain missing or wrong version | Run `make test-nolang` or install the toolchain |
| Native access warning | Running the JAR without the flag | Add `--enable-native-access=ALL-UNNAMED` |
| Package name has `unknown` arch | Unsupported `uname -m` | Set `SIMPLEIDE_PKG_ARCH` |
| Windows package has no `.exe` | Default is `app-image` | Set `SIMPLEIDE_PKG_TYPE=exe` |

## Continuous integration

CI workflows run `make test` and, on JDK 17, `make pkg-verify` with the expected
OS and architecture. See [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases) and the workflow files under
`.github/workflows/`.

The Linux workflows check out the repository on the runner and run the build
inside an explicit Debian 10 container, so the oldest supported glibc is tested
on every pull request:

| Workflow | Docker platform | `SIMPLEIDE_PKG_ARCH` |
| --- | --- | --- |
| `linux64.yml` | `linux/amd64` | `x64` |
| `linux32.yml` | `linux/386` | `x86` |
| `linuxarm.yml` | `linux/arm64` | `arm64` |

`scripts/ci/linux-debian.sh` configures the archived Debian 10 repositories and
installs the JDK, Go, Node.js, GCC, Mono, Rust and Python toolchains inside the
container. The job does not rely on the runner image for any language
toolchain. The 32-bit job adds `--security-opt seccomp=unconfined` because the
default seccomp profile on newer runner images blocks `socketcall(2)`; Debian's
i386 glibc still uses that syscall to create sockets, so without the override
the container has no network and apt fails to reach `archive.debian.org`. The
64-bit and ARM64 containers use the default profile and `--network host`. See
[Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux).
