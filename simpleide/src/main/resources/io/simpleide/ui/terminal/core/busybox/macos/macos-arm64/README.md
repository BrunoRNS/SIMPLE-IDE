# macOS Apple Silicon BusyBox

## Binary

* Path: `bin/busybox`
* Format: 64-bit ARM64 Mach-O executable
* BusyBox version: 1.20.0.git (build timestamp: 2023-11-29 17:38:23 PST)
* Symbols: Mach-O symbol table present (1,736 entries). The executable is
  not stripped of its symbol table; its full debug-information status was not
  independently verified.

## Source

* Archive: `busybox-macos-arm64-src.tar.bz2`
* Reference: https://github.com/kernelzeroday/busybox-macos-arm64
* Provenance: the archive name does not indicate Simple IDE-specific
  modifications. It is documented here as the source snapshot of that
  macOS/ARM64 port; no separate Simple IDE patch list is available.

Extract the archive with `tar -xjf <archive> -C <destination>`. See the root
`README.txt` for complete extraction notes and `LICENSE.txt` for the
BusyBox GPL-2.0-only license.

