# Script installation

The repository ships two download helpers:

| Script | Platform | Download method |
| --- | --- | --- |
| `scripts/install-simpleide.sh` | Linux and macOS | `curl` |
| `scripts/install-simpleide.ps1` | Windows | PowerShell `Invoke-RestMethod` / `Invoke-WebRequest` |

They **validate the host**, resolve the **latest GitHub release**, choose the
most advanced published variant for that host and leave the archive in the
chosen directory. They do **not** extract, install, register or execute the
package. Use them when you want the right download without reading the whole
release asset list.

The naming of the assets is documented in
[Releases](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases); the
media variants are explained in
[JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media).

## Quick start

Linux or macOS:

```sh
bash scripts/install-simpleide.sh
```

Windows PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-simpleide.ps1
```

Both scripts print the repository, tag, detected system and selected asset.
A successful run ends with `Saved: <path>` and a reminder that the archive was
not extracted or executed.

## What the scripts validate

### Linux

| Check | Requirement | Reason |
| --- | --- | --- |
| libc | glibc, not musl | The bundled JDK, terminal binaries and OpenJFX link against glibc |
| glibc version | 2.28+ for every package | Overall application baseline (Debian 10+) |
| glibc version | 2.34+ for JavaFX on `arm64` | OpenJFX `arm64` natives require it (Debian 12+) |
| architecture | `x64` (amd64), `arm64` or `x86` | These are the published Linux architectures |

The glibc version is read with `getconf GNU_LIBC_VERSION`, with a `ldd --version`
fallback. Alpine and other musl-based systems fail early with a clear message.

### macOS

| Check | Requirement | Reason |
| --- | --- | --- |
| version | macOS 11 (Big Sur)+ | Minimum for the JDK 17 `.dmg` |
| version | macOS 12+ for `javafx23` | The JDK 21 `.dmg` declares macOS 12 |
| architecture | `x64` or `arm64` | The published macOS architectures |

The version is read with `sw_vers -productVersion`.

### Windows

| Check | Requirement | Reason |
| --- | --- | --- |
| version | Windows 10+ on `x64`/`x86` | Minimum for the bundled JDK launcher |
| version | Windows 11 (build 22000+) on `arm64` | Only Windows 11 ARM64 is supported |
| architecture | `x64`, `x86` or `arm64` from the OS, not the PowerShell process | Avoids selecting the wrong package when 32-bit PowerShell runs on 64-bit Windows |

## Selection order

The scripts try variants in order and stop at the first asset present in the
release. A missing asset is reported as `Not published: <name>` and the next
variant is tried.

| System | Variant order |
| --- | --- |
| Linux `x64` | `javafx23` → `javafx22` → `nojavafx` |
| Linux `arm64`, glibc 2.34+ | `javafx23` → `javafx22` → `nojavafx` |
| Linux `arm64`, glibc 2.28–2.33 | `nojavafx` only |
| Linux `x86` | `nojavafx` only |
| macOS `x64`/`arm64`, macOS 12+ | `javafx23` → `javafx22` → `nojavafx` |
| macOS `x64`/`arm64`, macOS 11 | `javafx22` → `nojavafx` |
| Windows `x64` | `javafx23` → `javafx22` → `nojavafx` |
| Windows `x86`/`arm64` | `nojavafx` only |

`nojavafx` is tried even when that variant is not currently published for a
platform. If no compatible asset exists, the script exits with an error listing
the variants it tried; it never downloads a package for the wrong architecture.

On Linux the script prefers `.tar.gz` before `.zip` when a `.tar.gz` asset
exists (current releases publish `.zip`). macOS uses `.dmg`. The Windows script
downloads `.zip`; native `.msi` installers are not selected automatically.

## Options and environment variables

| Shell script | PowerShell | Meaning |
| --- | --- | --- |
| `SIMPLEIDE_TAG` | `-Tag` / `SIMPLEIDE_TAG` | Use a specific tag instead of the latest release |
| `SIMPLEIDE_OUTPUT_DIR` | `-OutputDir` / `SIMPLEIDE_OUTPUT_DIR` | Destination directory (default: current directory) |
| `SIMPLEIDE_FORCE=1` | `-Force` / `SIMPLEIDE_FORCE=1` | Overwrite a file with the same name |
| `--help` | `-Help` | Print usage |

Examples:

```sh
# Pin a release and choose a destination.
SIMPLEIDE_TAG=v1.1.0 SIMPLEIDE_OUTPUT_DIR="$HOME/Downloads" \
  bash scripts/install-simpleide.sh

# Re-download over an existing file.
SIMPLEIDE_FORCE=1 bash scripts/install-simpleide.sh
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-simpleide.ps1 `
  -Tag v1.1.0 -OutputDir "$env:USERPROFILE\Downloads" -Force
```

The default tag comes from
`https://api.github.com/repos/BrunoRNS/SIMPLE-IDE/releases/latest`, so GitHub
pre-releases are skipped. Set `SIMPLEIDE_TAG` to download a pre-release such as
`v1.1.0beta`.

## How release discovery works

1. Query the GitHub releases API for the latest tag (or use the override).
2. Strip the leading `v` to build the asset prefix
   `simpleide-v<version>-<os>-<arch>-<variant>`.
3. Check candidate URLs with an HTTP HEAD request.
4. Download the first hit with `curl` (Linux/macOS) or `Invoke-WebRequest`
   (Windows) into a temporary file in the destination directory.
5. Move the verified, non-empty file to its final name.

GitHub download URLs redirect to the release asset CDN; both scripts follow the
redirect. No GitHub token is needed for public releases, but the API may apply
rate limits to unauthenticated requests. A specific `SIMPLEIDE_TAG` avoids the
API lookup entirely.

## Finishing the installation manually

The scripts stop after downloading. To use the file:

### Linux (`.zip`)

```sh
unzip simpleide-v1.1.0-linux-x64-javafx23.zip -d SimpleIDE
./SimpleIDE/SimpleIDE/bin/SimpleIDE
```

For a `.tar.gz` asset:

```sh
tar -xzf simpleide-v1.1.0-linux-x64-javafx23.tar.gz
./SimpleIDE/bin/SimpleIDE
```

The `.deb` and `.rpm` installers are published in the same release but are not
selected by the script; install them with your package manager if you prefer a
system-wide installation. See
[Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux).

### macOS (`.dmg`)

```sh
open simpleide-v1.1.0-macos-arm64-javafx23.dmg
```

Drag `SimpleIDE.app` to Applications. The `.dmg` declares the minimum macOS
version in `Info.plist`; see
[macOS compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-macOS).

### Windows (`.zip`)

```powershell
Expand-Archive simpleide-v1.1.0-windows-x64-javafx23.zip -DestinationPath SimpleIDE
.\SimpleIDE\SimpleIDE\SimpleIDE.exe
```

The release also contains a `windows-x64` `.msi`; the script intentionally
downloads the portable zip instead. See
[Windows compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows).

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `curl is required` | `curl` is not installed | Install `curl` and retry |
| `musl-based systems are not supported` | Alpine or another musl distribution | Use a glibc distribution |
| `Simple IDE requires glibc 2.28+` | Distribution older than Debian 10 | Upgrade the distribution |
| `no compatible package was published` | The tag has no asset for this OS/arch, or the variant is unavailable | Check the [release page](https://github.com/BrunoRNS/SIMPLE-IDE/releases), or pin another tag |
| `Simple IDE ARM64 requires Windows 11` | Windows 10 ARM64 | Upgrade to Windows 11 |
| `already exists` | A file with the same name is present | Move it, delete it or pass `-Force` / `SIMPLEIDE_FORCE=1` |
| API rate-limit error | Too many unauthenticated GitHub API calls | Retry later or set `SIMPLEIDE_TAG` |

## Related documentation

- [Releases and artifact names](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Releases)
- [JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media)
- [Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux)
- [Windows compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Windows)
- [macOS compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-macOS)
