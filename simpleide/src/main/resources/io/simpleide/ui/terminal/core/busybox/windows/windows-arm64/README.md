# Windows ARM64 BusyBox

## Binary

* Path: `bin/busybox.exe`
* Format: ARM64 PE console executable
* BusyBox version: 1.39.0-FRP-6354-g5f09fb446
  (build timestamp: 2026-10-27 09:12:06 UTC)
* Symbols: the PE header is not marked debug-stripped, but the COFF symbol
  table is empty. The exact strip status is uncertain.

## Source

* Archive: `busybox-w32-src.tar.bz2`
* Reference: https://frippery.org/busybox/
* Provenance: the archive name does not indicate Simple IDE-specific
  modifications. It is the shared source snapshot for the bundled busybox-w32
  Windows builds.

Extract the archive with `tar -xjf <archive> -C <destination>` or use 7-Zip.
See the root `README.txt` for extraction notes and `LICENSE.txt` for the
BusyBox GPL-2.0-only license.
