# Windows compatibility

Simple IDE runs on **Windows 10 or newer** on 32-bit and 64-bit x86, and on
**Windows 11** on ARM64. The packaged builds include a private Java runtime, so
users do not need to install a JDK.

## Minimum version

| Architecture | Artifact suffix | Minimum Windows version |
| --- | --- | --- |
| `x86` (32-bit) | `windows-x86` | Windows 10 |
| `x64` | `windows-x64` | Windows 10 |
| `arm64` | `windows-arm64` | Windows 11 |

The ARM64 build is native: it targets Windows on Arm devices such as
Snapdragon X Elite systems. Windows 10 on Arm is not supported.

Windows 7, Windows 8 and Windows 8.1 are not supported. The JDK 17+ vendors no
longer support those releases, and the bundled runtime uses APIs that are only
available on Windows 10 and newer.

## Package support

| Architecture | Static zip | `.msi` installer | JDK versions validated in CI |
| --- | --- | --- | --- |
| `x64` | Yes | Yes | 17, 21, 25, 27 |
| `x86` (32-bit) | Yes | Yes | 17 |
| `arm64` | Yes | Yes | 17, 21, 25, 27 |

The static zip contains the same application image as the installer. Unzip it
and run `SimpleIDE.exe`; no installation step is required. The `.msi` installer
registers the application and adds it to the Start menu.

Current JDK vendors no longer publish 32-bit Windows builds for Java 21 and
newer, so the 32-bit packages are validated with JDK 17.

## Windows-specific notes

- The packages are not code-signed. Windows SmartScreen may show an
  "unknown publisher" warning the first time the installer or the portable
  launcher runs; choose **More info** and then **Run anyway**.
- The 32-bit build also runs on 64-bit Windows through WOW64, but the `x64`
  build is faster and is the recommended choice there.
- Long project paths are limited by the Windows path length. Enable long path
  support in Windows or keep projects in a short directory when a tool reports
  path errors.
- The embedded terminal uses the native pty4j library selected for the package
  architecture; do not mix the launcher with a runtime from another
  architecture.

## Continuous integration

| Workflow | Runner | Architecture |
| --- | --- | --- |
| `.github/workflows/windows64.yml` | `windows-latest` | `x64` |
| `.github/workflows/windows32.yml` | `windows-latest` | `x86` |
| `.github/workflows/windowsarm.yml` | `windows-11-arm` | `arm64` |

The `x64` and `arm64` workflows run the test suite on JDK 17, 21, 25 and 27;
the 32-bit workflow runs it on JDK 17. Every workflow builds its packages with
JDK 17 and validates them with `scripts/validate-package.ps1`. The zip check
extracts the archive and confirms that the launcher, bundled JVM, runtime
library and bundled `java.exe` are valid PE files for the requested
architecture, and that the runtime provides the `jdk.compiler` module. Packaging
overrides jpackage's default `--strip-native-commands` jlink option so the
embedded compiler and Java language server work without an external JDK. The
MSI check reads the installer database, confirms that `ProductName` identifies
SimpleIDE, and checks the Summary Information platform (`Intel`, `x64` or
`Arm64`).
`candle.exe` from the WiX toolset is required to build the MSI and is installed
by `scripts/ci/ensure-wix.ps1`.

jpackage chooses the WiX target from `os.arch`, which reports the host OS
architecture rather than the JVM data model. A 32-bit JDK on 64-bit Windows
would therefore produce an x64 MSI; `scripts/package.ps1` passes
`-J-Dos.arch=x86` for x86 builds so WiX emits Intel components and an Intel
template. jpackage has no ARM64 WiX target at all: every non-x86 JVM takes the
hard-coded `-arch x64` path. ARM64 packages are still correct because x64 and
ARM64 MSI component metadata are both 64-bit, so after building the installer
`scripts/package.ps1` rewrites the Summary Information platform to `Arm64` and
sets Page Count and `Package.InstallerVersion` to 500. The patched MSI refuses
to install on x64 Windows.

CI does not upload the generated packages. The release workflow rebuilds them
and attaches the resulting files to the GitHub release tag.

## See also

- [Build, test and packaging](../build/MAKEFILE.md)
- [Releases](../build/RELEASES.md)
- [Linux compatibility](LINUX.md)
- [macOS compatibility](MACOS.md)
