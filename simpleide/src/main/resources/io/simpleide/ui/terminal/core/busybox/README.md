# Bundled BusyBox Executables

This directory contains the BusyBox binaries embedded by Simple IDE's terminal,
the corresponding source archives, and third-party license notices. The
terminal selects one binary by operating system and CPU architecture:

| Platform | Executable | Bundled version | Symbol/strip information |
| --- | --- | --- | --- |
| Linux x86 | `linux/linux-x86/bin/busybox` | 1.20.0 | Unstripped; ELF metadata reports debug information |
| Linux x86-64 | `linux/linux-x64/bin/busybox` | 1.20.0 | Unstripped; ELF metadata reports debug information |
| Linux ARM64 | `linux/linux-arm64/bin/busybox` | 1.20.0 | Stripped |
| macOS Intel x86-64 | `macos/macos-intel/bin/busybox` | 1.20.0.git | Mach-O symbol table present; debug-information status was not independently verified |
| macOS Apple Silicon ARM64 | `macos/macos-arm64/bin/busybox` | 1.20.0.git | Mach-O symbol table present; debug-information status was not independently verified |
| Windows x86 | `windows/windows-x86/bin/busybox.exe` | 1.39.0-FRP-6354-g5f09fb446 | Stripped to an external PDB |
| Windows x86-64 Unicode | `windows/windows-x64-unicode/bin/busybox.exe` | 1.39.0-FRP-6354-g5f09fb446 | Stripped to an external PDB |
| Windows ARM64 | `windows/windows-arm64/bin/busybox.exe` | 1.39.0-FRP-6354-g5f09fb446 | PE header is not marked debug-stripped; no COFF symbol table is present, so the exact strip status is uncertain |

Version strings and build timestamps were read from the bundled executables.
The source archive version or revision can differ from the build timestamp.
For provenance and source details, see the README in each platform directory.

## Directory contents

Each operating-system/architecture directory contains:

* `bin/busybox` or `bin/busybox.exe`: the executable used by the terminal.
* A `.tar.bz2` archive containing the associated BusyBox source tree.
* A `README.txt` with the source URL, observed version, and any recorded
  modification notes.
* A `LICENSE.txt` (or `LICENSE` in the Windows x86 directory) where the
  BusyBox GPL-2.0 license text is included.

The source archive names are intentional. Names containing `modified`,
`simpleide`, or other modification notes indicate a modified source snapshot.
When the archive name does not indicate local changes, this documentation
identifies it as an upstream/reference source snapshot; it does not claim that
the source was independently compared against an upstream release.

## Extracting a source archive

The archives are bzip2-compressed tar files. From the directory containing the
archive, first list its contents if you want to check its top-level layout:

```sh
tar -tjf <source-archive>.tar.bz2
```

Extract it to a new directory on Linux or macOS:

```sh
mkdir -p busybox-source
tar -xjf <source-archive>.tar.bz2 -C busybox-source
```

On Windows, use a recent PowerShell/Windows installation with `tar` available:

```powershell
New-Item -ItemType Directory -Force .\busybox-source
tar -xjf .\busybox-w32-src.tar.bz2 -C .\busybox-source
```

If `tar` is unavailable, 7-Zip can extract the `.bz2` layer first and then the
resulting `.tar` archive. The archives have different top-level directory
layouts, so inspect the extracted directory before building. Reproducing a
binary may also require the original compiler, SDK, configuration, and build
environment; consult the extracted tree and the source project's documentation.

## Licensing

BusyBox is a separate third-party component distributed under GNU GPL version 2
only. This does not change the license of Simple IDE's own code. The notice in
`THIRD_PARTY_LICENSES.txt` explains the separation, and the platform folders
contain copies of the BusyBox license text.