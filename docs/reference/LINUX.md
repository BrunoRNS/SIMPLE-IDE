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
newer, so the 32-bit packages are validated with JDK 17.

## Wayland

Swing applications use the X11 protocol. On a Wayland session, run Simple IDE
through XWayland; this is the mode Oracle documents for the certified JDKs. A
pure Wayland compositor without XWayland is not supported.

## Continuous integration

Every Linux workflow builds and tests inside an explicit Debian 10 container so
the minimum glibc is exercised on real hardware or emulation:

| Workflow | Container platform | Architecture |
| --- | --- | --- |
| `.github/workflows/linux64.yml` | `linux/amd64` | `x64` |
| `.github/workflows/linux32.yml` | `linux/386` | `x86` |
| `.github/workflows/linuxarm.yml` | `linux/arm64` | `arm64` |

The repository is checked out on the runner and mounted into the container.
Every language toolchain (JDK, Go, Node.js, GCC, Mono, Rust and Python) is
installed inside that container by `scripts/ci/linux-debian.sh`, so the tests do
not depend on the runner image. Docker runs with `--network host` so the
container reuses the runner's resolver and can resolve `archive.debian.org`;
the systemd-resolved stub on `127.0.0.53` is not reachable over the default
bridge network. Alpine is not used because GitHub JavaScript actions cannot run
in a 32-bit container and the JDK cannot run on musl.

## References

- [Oracle JDK 27 certified system configurations](https://www.oracle.com/java/technologies/javase/products-doc-jdk27certconfig.html)
- [Oracle JDK 27 installation guide for Linux](https://docs.oracle.com/en/java/javase/27/install/installation-jdk-linux-platforms.html)
- [Bundled BusyBox](../third-party/BUSYBOX.md)
- [Terminal API](../api/terminal.md)
- [Build and test with Make](../build/MAKEFILE.md)
