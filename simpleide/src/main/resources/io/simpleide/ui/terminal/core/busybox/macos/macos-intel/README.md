# macOS Intel BusyBox

## Binary

* Path: `bin/busybox`
* Format: 64-bit Intel x86-64 Mach-O executable
* BusyBox version: 1.20.0.git (build timestamp: 2026-11-06 14:07:25 PST)
* Symbols: Mach-O symbol table present (12,698 entries). The executable is
  not stripped of its symbol table; its full debug-information status was not
  independently verified.

## Source

* Archive: `busybox-macos-intel-simpleide-src.tar.bz2`
* Reference: https://github.com/daliworks/busybox-osx/
* Provenance: modified for Simple IDE, as indicated by the archive name. The
  specific source changes are not itemized.

The binary was built for Intel macOS. Its source project describes an OSX
BusyBox port and provides build instructions. Extract the archive with
`tar -xjf <archive> -C <destination>`; see the root `README.txt` for details.
The license text is in `LICENSE.txt`.
