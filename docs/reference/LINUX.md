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
| Audio/video (optional) | glibc 2.17+ on `x64`; glibc 2.34+ on `arm64` | OpenJFX native libraries. JavaFX on `arm64` needs Debian 12+; the `nojavafx` baseline still needs only glibc 2.28. See [Audio and video playback](#audio-and-video-playback) |

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
before running Simple IDE. On `arm64`, upgrade to glibc 2.34+ (Debian 12+)
before selecting a `javafx22` or `javafx23` package.

## Package support

| Architecture | Static zip | `.deb` / `.rpm` | Release variants | JDK versions validated in CI |
| --- | --- | --- | --- | --- |
| `x64` (amd64) | Yes | Yes | `javafx23`, `javafx22`, `nojavafx` | 17, 21, 25, 27 |
| `arm64` | Yes | Yes | `javafx23` (Debian 12+), `javafx22` (Debian 12+), `nojavafx` (Debian 10+) | 17, 21, 25, 27 for the baseline; 17/21 for JavaFX release builds |
| `x86` (32-bit) | Yes | Yes | `nojavafx` | 17 |

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
viewers are compiled and packaged only where OpenJFX publishes usable native
artifacts and the selected Maven variant enables them. Everywhere else the build
does not download JavaFX, audio and video files open in the hex editor, and a
warning is written to the log.

### Variants

The build has three media variants:

| Variant | Meaning |
| --- | --- |
| `javafx22` | OpenJFX 22.0.2 |
| `javafx23` | OpenJFX 23.0.2 |
| `nojavafx` | No JavaFX; media files use the hex editor |

`make` selects the variant from the JDK and platform in
`scripts/javafx-version.sh` and forwards it to Maven as
`-Dsimpleide.media.variant=<variant>`:

| JDK | Selected variant | Condition |
| --- | --- | --- |
| 17 through 20 | `javafx22` | Linux `x64` |
| 21 or newer | `javafx23` | Linux `x64` |
| Any | `nojavafx` | Linux `arm64` (automatic), Linux `x86`, or an explicit `nojavafx` |

Automatic Linux `arm64` builds are `nojavafx` to keep the Debian 10 glibc 2.28
baseline honest. Unlike Linux `x86`, however, `arm64` has forced
`javafx22`/`javafx23` profiles: they are used by the release workflow on Debian
12 and can be requested locally with `SIMPLEIDE_MEDIA_VARIANT`. The script
rejects a forced JavaFX build below glibc 2.34 and rejects `javafx23` below JDK
21 instead of silently downgrading to `nojavafx`. Linux `x86` still has no
usable OpenJFX natives, so forced variants resolve to `nojavafx` there.

Set `SIMPLEIDE_MEDIA_VARIANT=nojavafx|javafx22|javafx23` to override the
automatic choice. `SIMPLEIDE_JAVAFX_DISABLE=1` is a legacy alias for
`nojavafx`, and `SIMPLEIDE_JAVAFX_VERSION` remains a legacy version override
(`22.*` maps to `javafx22`, `23.*` to `javafx23`, `none` to `nojavafx`). The
Maven consumer contract is documented in
[JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media).

### glibc requirements of the JavaFX binaries

The repository was audited against the native libraries shipped in the OpenJFX
artifacts:

| OpenJFX | `linux` (x64) max symbol | `linux-aarch64` max symbol | FFmpeg plug-ins |
| --- | --- | --- | --- |
| 22.0.2 | `GLIBC_2.17` | `GLIBC_2.34` | `libavcodec`/`libavformat` 56 through 60 |
| 23.0.2 | `GLIBC_2.17` | `GLIBC_2.34` | `libavcodec`/`libavformat` 56 through 61 |

The application requires glibc 2.28. On `x64`, the JavaFX natives need only
glibc 2.17, so they work on every supported distribution. On `arm64`, the
OpenJFX natives need glibc 2.34: Debian 12 (bookworm, glibc 2.36) is the oldest
release used for those packages. The `nojavafx` `arm64` packages stay on Debian
10 (glibc 2.28). The rest of the IDE runs normally in both cases; only media
playback changes.

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
no ALSA configuration defines a default PCM (in `~/.asoundrc`,
`/etc/asound.conf`, `/etc/alsa/conf.d` or `/usr/share/alsa/alsa.conf.d`), the
viewers create a temporary ALSA configuration that routes the default PCM
through the matching plug-in: PipeWire when its ALSA module is installed,
otherwise PulseAudio. Set `ALSA_CONFIG_PATH` to override the automatic
configuration.

### Headless and packaging behavior

- In a headless session the viewers show that a graphical environment is
  required, and playback tests are skipped.
- The automatic Maven build does not resolve JavaFX on Linux `arm64`, 32-bit
  `x86` Linux, 32-bit Windows, Windows `arm64` or unsupported architectures.
  Forced `javafx22`/`javafx23` profiles exist for Linux `arm64`.
- Linux packages are suffixed with the selected variant: `-javafx22`,
  `-javafx23` or `-nojavafx` (for example
  `simpleide-v1.1.0-linux-x64-javafx23.deb` or
  `simpleide-v1.1.0-linux-arm64-javafx22.deb`). Linux `x86` packages are always
  `-nojavafx`.
- The `x64` packages and the `arm64` `nojavafx` package are built on the Debian
  10 glibc 2.28 baseline. The `arm64` `javafx22` and `javafx23` packages are
  built on Debian 12 because their OpenJFX natives need glibc 2.34.

## Continuous integration

The pull-request workflows build and test inside an explicit Debian 10 container
so the minimum glibc is exercised on real hardware or emulation. The release
workflow adds Debian 12 jobs for the JavaFX `arm64` variants:

| Workflow | Container platform | Architecture | Container image |
| --- | --- | --- | --- |
| `.github/workflows/linux64.yml` | `linux/amd64` | `x64` | `debian:10` |
| `.github/workflows/linux32.yml` | `linux/386` | `x86` | `debian:10` |
| `.github/workflows/linuxarm.yml` | `linux/arm64` | `arm64` | `debian:10` |
| `.github/workflows/release.yml` | `linux/amd64` | `x64` (`nojavafx`/`javafx22`/`javafx23`) | `debian:10` |
| `.github/workflows/release.yml` | `linux/arm64` | `arm64` (`nojavafx`) | `debian:10` |
| `.github/workflows/release.yml` | `linux/arm64` | `arm64` (`javafx22`/`javafx23`) | `debian:12` |

The repository is checked out on the runner and mounted into the container.
Every language toolchain (JDK, Go, Node.js, GCC, Mono, Rust and Python) and the
media runtime libraries (GTK 3, ALSA and FFmpeg) are installed inside that
container by `scripts/ci/linux-debian.sh`, so the tests do not depend on the
runner image. The container image is selected with `SIMPLEIDE_DEBIAN_IMAGE`,
and the media variant is forwarded with `SIMPLEIDE_MEDIA_VARIANT`. All
containers use host networking. The 32-bit
job additionally passes `--security-opt seccomp=unconfined`: newer runner
images ship a Docker default seccomp profile that denies `socketcall(2)`, and
Debian's i386 glibc still creates sockets through that syscall. Without the
override the 32-bit container has no network and apt reports a misleading DNS
failure for `archive.debian.org`. Alpine is not used because GitHub JavaScript
actions cannot run in a 32-bit container and the JDK cannot run on musl.

## Downloading the right package

The repository provides `scripts/install-simpleide.sh` for Linux and macOS. It
checks glibc, architecture and macOS version, then downloads the best published
asset (`javafx23` → `javafx22` → `nojavafx`) without extracting or running it.
See [Script installation](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Install).

## References

- [Oracle JDK 27 certified system configurations](https://www.oracle.com/java/technologies/javase/products-doc-jdk27certconfig.html)
- [Oracle JDK 27 installation guide for Linux](https://docs.oracle.com/en/java/javase/27/install/installation-jdk-linux-platforms.html)
- [Bundled BusyBox](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-BusyBox)
- [Terminal API](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Terminal)
- [Build and test with Make](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Makefile)
