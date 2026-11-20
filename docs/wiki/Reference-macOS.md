# macOS compatibility

Simple IDE supports **macOS Big Sur (11.0) or newer** on Intel (`x64`) and
Apple Silicon (`arm64`) Macs. The packaged `.dmg` includes a private Java
runtime, so users do not need to install a JDK.

The minimum macOS version depends on the JDK used to build the package, because
the bundled runtime only runs on the releases supported by that JDK.

## Minimum version by JDK

| JDK used for packaging | Minimum macOS version | Name |
| --- | --- | --- |
| 17 | 11.0 | Big Sur |
| 21 | 12.0 | Monterey |
| 25 | 14.0 | Sonoma |
| 27 | 15.0 | Sequoia |

The published release `.dmg` is built with **JDK 17** and declares macOS
**11.0** as its minimum, so it runs on Big Sur on both `x86_64` and `arm64`.
Apple Silicon requires Big Sur or newer in any case, so the JDK 17 build is the
broadest release for both architectures.

Building with a newer JDK raises the minimum: a JDK 21 package requires
Monterey, a JDK 25 package requires Sonoma and a JDK 27 package requires
Sequoia. The fat JAR follows the JDK that runs it, not the packaging JDK.

macOS Catalina (10.15) and older are not supported.

## How the minimum is declared

`jpackage` does not expose an option for `LSMinimumSystemVersion`, so
`scripts/package.sh` builds the application image first and writes the value
from the table into `SimpleIDE.app/Contents/Info.plist`. The `.dmg` is then
created from that prepared image with `jpackage --app-image`.

`scripts/validate-package.sh` mounts the `.dmg`, checks the launcher, bundled
JVM and bundled Java launcher architectures, verifies that the runtime provides
the `jdk.compiler` module, reads `LSMinimumSystemVersion` with `plutil` and
fails when it does not match the value expected for the packaging JDK. This is
how CI guarantees that the JDK 17 release image stays compatible with Big Sur
even though the runner itself uses a newer macOS.

Set `SIMPLEIDE_MACOS_MIN_VERSION` to override the computed value when building
a custom package, for example:

```sh
SIMPLEIDE_MACOS_MIN_VERSION=12.0 make pkg
```

## Package support

| Architecture | `.dmg` installer | JDK versions tested in CI | JDK used for the release |
| --- | --- | --- | --- |
| `x64` | Yes | 17, 21, 25, 27 | 17 |
| `arm64` | Yes | 17, 21, 25, 27 | 17 |

The `x64` `.dmg` also runs on Apple Silicon through Rosetta 2. The `arm64`
`.dmg` is native and is the recommended build for Apple Silicon Macs.

## macOS-specific notes

- The packages are not code-signed or notarized. Gatekeeper may block the first
  launch; open **System Settings → Privacy & Security**, or Control-click the
  application and choose **Open**.
- Use the `.dmg` that matches the machine architecture for the best
  performance. Mixing an `x64` application image with an `arm64` runtime is not
  supported.
- The embedded terminal uses the native pty4j library for the package
  architecture.

## Continuous integration

| Workflow | Runner | Architecture |
| --- | --- | --- |
| `.github/workflows/macos-intel.yml` | `macos-15-intel` | `x64` |
| `.github/workflows/macos-arm.yml` | `macos-latest` | `arm64` |

Both workflows run the test suite on JDK 17, 21, 25 and 27. The JDK 17 jobs
also build and validate the packages with `make pkg-verify`, which checks the
declared minimum macOS version. Runners stay on recent macOS releases; the
declared minimum is validated from the package instead of from the runner
version.

The generated packages are uploaded as `packages-macos-<arch>` only after every
previous step succeeds and are kept for one day.

## See also

- [Build, test and packaging](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Makefile)
- [Releases](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases)
- [Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux)
- [Windows compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows)
