# Linux compatibility

Simple IDE runs on GNU/Linux distributions that provide **glibc 2.28 or newer**.
The application is distributed as a self-contained image with a bundled JDK and
a bundled BusyBox terminal, but the native libraries still link against the
system C library.

Alpine Linux and other **musl-based** distributions (for example postmarketOS
or Wolfi) are **not supported**. The bundled JDK, the terminal binaries and the
32-bit pty4j native library are built against glibc; they cannot run on musl.

## Minimum version

| Requirement | Value | Why |
| --- | --- | --- |
| C library | GNU glibc 2.28+ | Required by the bundled JDK and the 32-bit pty4j native library |
| Architecture | `x86` (32-bit), `x64` or `arm64` | The terminal ships one BusyBox binary per architecture |
| Java | JDK 17+ | The application is compiled for Java 17 and runs on 17, 21, 25 and 27 |
| Display | X11 or Wayland with XWayland | Swing runs through the X11 protocol |
| Audio/video (optional) | glibc 2.17+ on `x64`, glibc 2.34+ on `arm64` | OpenJFX native libraries; see [Audio and video playback](#audio-and-video-playback) |

The 32-bit build is the strictest one: its pty4j native library requires
glibc 2.28+, so that version is the overall minimum. Oracle certifies JDK 27 on
Red Hat Enterprise Linux 8, Oracle Linux 8 and newer distributions; RHEL 8 also
ships glibc 2.28. See the Oracle documentation at the end of this page.

## Supported distributions

The table lists the oldest release that satisfies the minimum. Newer releases
of the same distribution are supported unless the vendor changed the C library
in an incompatible way.

| Distribution | Oldest supported release | glibc |
| --- | --- | --- |
| Debian | 10 (buster) | 2.28 |
| Ubuntu | 18.10 (cosmic); 20.04 LTS or newer recommended | 2.28 / 2.31 |
| Linux Mint | 20 (based on Ubuntu 20.04) | 2.31 |
| Fedora | 29 | 2.28 |
| Red Hat Enterprise Linux | 8 | 2.28 |
| CentOS Stream | 8 | 2.28 |
| Oracle Linux | 8 | 2.28 |
| Rocky Linux / AlmaLinux | 8 | 2.28 |
| Amazon Linux | 2023 | 2.34 |
| SUSE Linux Enterprise Server | 16 | 2.38+ |
| openSUSE Leap | 15.3 or newer | 2.31+ |
| Arch Linux | Current rolling release | Current |
| Raspberry Pi OS | 64-bit, Bullseye or newer | 2.31 |

Not supported:

- Alpine Linux and every other musl-based distribution.
- 32-bit ARM (`armhf`): Simple IDE ships an `arm64` terminal binary only.
- Debian 9, Ubuntu 18.04, Linux Mint 19, Fedora 28, RHEL/CentOS 7, Amazon
  Linux 2 and SUSE Linux Enterprise Server 15 — their glibc is older than 2.28.

## Check the installed version

```sh
getconf GNU_LIBC_VERSION
# or
ldd --version | head -n 1
```

If the command reports a version lower than 2.28, upgrade the distribution
before running Simple IDE.

## Package support

| Architecture | Static zip | `.deb` / `.rpm` | JDK versions validated in CI |
| --- | --- | --- | --- |
| `x64` | Yes | Yes | 17, 21, 25, 27 |
| `arm64` | Yes | Yes | 17, 21, 25, 27 |
| `x86` (32-bit) | Yes | Yes | 17 |

Current JDK vendors no longer publish 32-bit Linux builds for Java 21 and
newer, so the 32-bit packages are validated with JDK 17. Package validation
also checks that the bundled runtime keeps its Java launcher and the
`jdk.compiler` module, which the embedded compiler and Java language server
need.

## Wayland

Swing applications use the X11 protocol. On a Wayland session, run Simple IDE
through XWayland; this is the mode Oracle documents for the certified JDKs. A
pure Wayland compositor without XWayland is not supported.

## Audio and video playback

Audio and video playback is an optional feature built on JavaFX. The JavaFX
viewers are compiled and packaged only where OpenJFX publishes native
artifacts, and the build does not download JavaFX on any other platform. When
media support is absent, audio and video files open in the hex editor and a
warning is written to the log.

### Version selection

The build selects JavaFX from the JDK and the platform in
`scripts/javafx-version.sh`; every `make` target forwards the result to Maven:

| JDK | Selected JavaFX | Condition |
| --- | --- | --- |
| 17 through 20 | 22.0.2 (LTS) | The platform glibc meets the minimum for that architecture |
| 21 or newer | 23.0.2 | The platform glibc meets the minimum for that architecture |
| 21 or newer | 22.0.2 (LTS) | JavaFX 23 does not meet the glibc minimum but JavaFX 22 does |
| Any | none | No OpenJFX native artifact exists, or neither version meets the glibc minimum |

The same source compiles against both JavaFX versions. `SIMPLEIDE_JAVAFX_VERSION`
forces a version and `SIMPLEIDE_JAVAFX_DISABLE=1` disables media.

### glibc requirements of the JavaFX binaries

The repository was audited against the native libraries shipped in the OpenJFX
artifacts:

| OpenJFX | `linux` (x64) max symbol | `linux-aarch64` max symbol | FFmpeg plug-ins |
| --- | --- | --- | --- |
| 22.0.2 | `GLIBC_2.17` | `GLIBC_2.34` | `libavcodec`/`libavformat` 56 through 60 |
| 23.0.2 | `GLIBC_2.17` | `GLIBC_2.34` | `libavcodec`/`libavformat` 56 through 61 |

The overall application still requires only glibc 2.28. On `x64`, JavaFX works on
every supported distribution. On `arm64`, JavaFX needs glibc 2.34, so media
playback requires Debian 12, Ubuntu 22.04 or newer; on an older `arm64` system
the application runs normally and media files fall back to the hex editor.

On Linux, JavaFX media loads the system FFmpeg shared libraries. Uncompressed
PCM formats use the bundled media stack, but MP3, AAC and H.264/H.265 playback
needs a compatible `libavcodec`/`libavformat` pair. JavaFX 22 accepts sonames 56
through 60; JavaFX 23 adds soname 61. Debian 10 ships FFmpeg 4.1
(`libavcodec.so.58`) and works with both. A distribution that ships FFmpeg 7
(`libavcodec.so.61`) needs the JavaFX 23 package.

### Required packages

The JavaFX graphics stack needs GTK 3, X11, GL, font and ALSA libraries;
media playback additionally needs FFmpeg.

Debian and Ubuntu:

```sh
sudo apt install ffmpeg libgtk-3-0 libasound2 libgl1 libxxf86vm1 libxtst6
```

Fedora and RHEL:

```sh
sudo dnf install ffmpeg gtk3 alsa-lib mesa-libGL libXxf86vm libXtst
```

Arch Linux:

```sh
sudo pacman -S ffmpeg gtk3 alsa-lib mesa
```

Check the detected FFmpeg libraries:

```sh
ldconfig -p | grep -E 'libavcodec|libavformat'
```

If no compatible library is present, the media player stays open and reports the
load error on its status label; the rest of the IDE is unaffected.

### Audio output

JavaFX writes audio through ALSA. When PulseAudio or PipeWire is available and
neither `~/.asoundrc` nor `/etc/asound.conf` defines a default device, the
viewers create a temporary ALSA configuration that routes the default PCM
through the PulseAudio ALSA plug-in. A native PipeWire ALSA configuration works
too. Set `ALSA_CONFIG_PATH` to override the automatic configuration.

### Headless and packaging behavior

- In a headless session the viewers show that a graphical environment is
  required, and playback tests are skipped.
- The Maven build does not resolve JavaFX on 32-bit `x86` Linux, 32-bit Windows,
  Windows `arm64` or unsupported architectures.
- Linux packages are suffixed with the selected variant: `-javafx22`,
  `-javafx23` or `-nojavafx` (for example
  `simpleide-v1.1.0-linux-x64-javafx23.deb`).
- The JavaFX 23 `arm64` package is built on Debian 12 so the produced package is
  validated against the glibc 2.34 floor. The JavaFX 22 `x64` package is built on
  Debian 10 and keeps the glibc 2.28 baseline.

## Continuous integration

Every Linux workflow builds and tests inside an explicit Debian 10 container so
the minimum glibc is exercised on real hardware or emulation:

| Workflow | Container platform | Architecture | Container image |
| --- | --- | --- | --- |
| `.github/workflows/linux64.yml` | `linux/amd64` | `x64` | `debian:10` |
| `.github/workflows/linux32.yml` | `linux/386` | `x86` | `debian:10` |
| `.github/workflows/linuxarm.yml` | `linux/arm64` | `arm64` | `debian:10`; `debian:12` for the JDK 21 package |

The repository is checked out on the runner and mounted into the container.
Every language toolchain (JDK, Go, Node.js, GCC, Mono, Rust and Python) and the
media runtime libraries (GTK 3, ALSA and FFmpeg) are installed inside that
container by `scripts/ci/linux-debian.sh`, so the tests do not depend on the
runner image. The container image is selected with `SIMPLEIDE_DEBIAN_IMAGE`;
the JavaFX 23 `arm64` package uses Debian 12 (glibc 2.36) because the OpenJFX
`arm64` natives require glibc 2.34. All containers use host networking. The 32-bit
job additionally passes `--security-opt seccomp=unconfined`: newer runner
images ship a Docker default seccomp profile that denies `socketcall(2)`, and
Debian's i386 glibc still creates sockets through that syscall. Without the
override the 32-bit container has no network and apt reports a misleading DNS
failure for `archive.debian.org`. Alpine is not used because GitHub JavaScript
actions cannot run in a 32-bit container and the JDK cannot run on musl.

## References

- [Oracle JDK 27 certified system configurations](https://www.oracle.com/java/technologies/javase/products-doc-jdk27certconfig.html)
- [Oracle JDK 27 installation guide for Linux](https://docs.oracle.com/en/java/javase/27/install/installation-jdk-linux-platforms.html)
- [Bundled BusyBox](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-BusyBox)
- [Terminal API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Terminal)
- [Build and test with Make](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Makefile)
