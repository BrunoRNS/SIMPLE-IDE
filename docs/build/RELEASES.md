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
| Windows `.msi` installer | `make pkg` on Windows CI | `simpleide-v<version>-windows-<arch>.msi` |

`<os>` is `linux`, `macos` or `windows`; `<arch>` is `x64`, `x86` or `arm64`.
`make pkg` builds the full set for the current OS: Linux produces the static
zip, the `.deb` and the `.rpm`; macOS produces only the `.dmg`; Windows
produces the static zip (whose launcher is the `SimpleIDE.exe` inside the app
image) and the `.msi` installer.

Before a release, every CI platform validates what it produces with
`make pkg-verify`. The validation extracts the static zip and checks its
launcher, and asks `dpkg-deb`, `rpm`, `hdiutil` or the Windows Installer
database to read the installer metadata. When a package is invalid, the CI job
fails and uploads the whole package directory as a debugging artifact
(`invalid-packages-<os>-<arch>`).

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
SIMPLEIDE_PKG_TYPE=msi make pkg    # Windows .msi
```

The release job uploads every artifact produced on each CI platform, so a
release contains the fat JAR plus one static zip and installer per supported
platform.

See also:

- [Build, test and packaging](MAKEFILE.md)
- [Third-party overview](../third-party/README.md)
