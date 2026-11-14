# Bundled BusyBox

The embedded terminal runs BusyBox as its shell. One binary per supported
platform is bundled inside the Simple IDE fat JAR under:

```text
simpleide/src/main/resources/io/simpleide/ui/terminal/core/busybox/
```

Nothing is downloaded at runtime: the terminal extracts the binary that matches
the current OS and architecture, runs it from a temporary directory, and deletes
it when the session ends. This keeps the terminal functional without network
access.

## Platform directory map

| Directory | Binary | Version | Source archive |
| --- | --- | --- | --- |
| `linux/linux-x86/` | `bin/busybox` | BusyBox 1.20.0 | `busybox-1.20.0-modified-simpleide-nommu-monotonicsyscall-oldkernel-src.tar.bz2` |
| `linux/linux-x64/` | `bin/busybox` | BusyBox 1.20.0 | `busybox-1.20.0-modified-simpleide-src.tar.bz2` |
| `linux/linux-arm64/` | `bin/busybox` | BusyBox 1.20.0 | `busybox-1.20.0-arm64-simpleide-src.tar.bz2` |
| `macos/macos-intel/` | `bin/busybox` | BusyBox 1.20.0.git (2026-11-06) | `busybox-macos-intel-simpleide-src.tar.bz2` |
| `macos/macos-arm64/` | `bin/busybox` | BusyBox 1.20.0.git (2023-11-29) | `busybox-macos-arm64-src.tar.bz2` |
| `windows/windows-x86/` | `bin/busybox.exe` | busybox-w32 v1.39.0-FRP | `busybox-w32-src.tar.bz2` |
| `windows/windows-x64-unicode/` | `bin/busybox.exe` | busybox-w32 v1.39.0-FRP | `busybox-w32-src.tar.bz2` |
| `windows/windows-arm64/` | `bin/busybox.exe` | busybox-w32 v1.39.0-FRP | `busybox-w32-src.tar.bz2` |

Every platform directory also contains a `README.txt` (provenance notes) and a
complete `LICENSE.txt`.

## License

All BusyBox binaries and their sources are licensed under the
**GNU General Public License version 2 (GPLv2)**:

- Linux and macOS builds come from the upstream
  [BusyBox project](https://busybox.net/).
- Windows builds come from
  [busybox-w32](https://frippery.org/busybox/), a Windows port that keeps the
  GPLv2 license.

Simple IDE is BSD 3-Clause and **does not link** BusyBox into its Java code:
the binaries are separate programs invoked as child processes. The GPLv2 source
archives ship inside the JAR precisely to satisfy the GPL's complete-corresponding-source
requirement when the JAR is redistributed.

## Redistribution rules

1. Do not strip the `LICENSE.txt` files or `*-src.tar.bz2` archives from the
   resources when you build or redistribute a JAR or jpackage package.
2. If you ship a jpackage image produced by `make pkg`, the resources are
   already inside the image; keep them intact.
3. When replacing a binary, update its `README.txt`, the platform
   `LICENSE.txt`, `VERSIONS.txt` and `THIRD_PARTY_LICENSES.txt` in the same
   change.

## Version inventory

The authoritative version table lives in
`simpleide/src/main/resources/io/simpleide/ui/terminal/core/busybox/VERSIONS.txt`.
Update it whenever a binary or its source archive changes.

See also:

- [Third-party overview](README.md)
- [Runtime downloads](RUNTIME.md)
- [License inventory](LICENSES.md)
