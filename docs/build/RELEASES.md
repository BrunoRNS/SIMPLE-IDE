# Releases

Every push to `main` triggers `.github/workflows/release.yml`, which builds and
publishes a GitHub release from the version in
`simpleide/src/main/resources/simpleide.properties`.

## Artifact naming

| Artifact | Produced by | Naming |
| --- | --- | --- |
| Executable fat JAR | `make jar` | `simpleide-v<version>-any-with-deps.jar` |
| Portable static package (zipped jpackage app image, no install required) | `make pkg` on Linux and Windows CI | `simpleide-v<version>-<os>-<arch>.zip` |
| Linux `.deb` installer | `make pkg` on Linux CI | `simpleide-v<version>-linux-<arch>.deb` |
| Linux `.rpm` installer | `make pkg` on Linux CI | `simpleide-v<version>-linux-<arch>.rpm` |
| macOS `.dmg` installer | `make pkg` on macOS CI | `simpleide-v<version>-macos-<arch>.dmg` |
| Windows `.msi` installer (x64 only) | `make pkg` on Windows CI | `simpleide-v<version>-windows-x64.msi` |

`<os>` is `linux`, `macos` or `windows`; `<arch>` is `x64`, `x86` or `arm64`.
`make pkg` builds the full set for the current OS: Linux produces the static
zip, the `.deb` and the `.rpm`; macOS produces only the `.dmg`; Windows
produces the static zip (whose launcher is the `SimpleIDE.exe` inside the app
image) and, on `x64` only, the `.msi` installer. Windows `x86` and `arm64`
release the static zip alone.

Before a release, every CI platform validates what it produces with
`make pkg-verify`. The validation extracts the static zip and checks its
launcher, bundled runtime architecture, bundled Java launcher and the
`jdk.compiler` module, and asks `dpkg-deb`, `rpm`, `hdiutil` or the Windows
Installer database to read the installer metadata. Packages keep jpackage's
native commands so the bundled JDK can run the embedded compiler and the Java
language server without an external JDK.
On `x64` the Windows check also confirms that the `.msi` declares `SimpleIDE`
as its `ProductName` and that its Summary Information platform is `x64`. No
MSI is built or validated for `x86` or `arm64`, which release the static zip
only. A failed job stops at the failing step.

CI jobs do not upload the generated packages as workflow artifacts. The
release workflow rebuilds every platform, passes the packages between its jobs
with `actions/upload-artifact`/`actions/download-artifact`, and attaches them
to the GitHub release tag with `gh release upload`.

Linux packages are built and validated inside an explicit Debian 10
(`buster`) container. Debian 10 provides the minimum supported glibc (2.28),
so a package that passes CI is known to run on every supported Linux
distribution. Alpine is not used. See
[Linux compatibility](../reference/LINUX.md).

## Version types

- A version containing `beta` (for example `1.0.0beta`) is published as a
  GitHub **pre-release**.
- Any other version is published as a **normal release**.
- The release tag is `v<version>`; pushing the same version again updates the
  existing release and re-uploads its artifacts.

## Package types

The portable zip is the default. A native installer can be built locally by
setting the package type:

```sh
SIMPLEIDE_PKG_TYPE=deb make pkg    # Linux .deb
SIMPLEIDE_PKG_TYPE=rpm make pkg    # Linux .rpm
SIMPLEIDE_PKG_TYPE=dmg make pkg    # macOS .dmg
SIMPLEIDE_PKG_TYPE=exe make pkg    # Windows .exe
SIMPLEIDE_PKG_TYPE=msi make pkg    # Windows .msi (x64 only)
```

The release job uploads every artifact produced on each CI platform, so a
release contains the fat JAR plus one static zip per supported platform, plus
the Linux, macOS and `windows-x64` installers.

See also:

- [Build, test and packaging](MAKEFILE.md)
- [Linux compatibility](../reference/LINUX.md)
- [Third-party overview](../third-party/README.md)
