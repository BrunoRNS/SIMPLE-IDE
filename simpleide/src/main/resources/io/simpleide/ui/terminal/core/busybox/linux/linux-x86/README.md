# Linux x86 BusyBox

## Binary

* Path: `bin/busybox`
* Format: 32-bit x86 ELF, static PIE
* BusyBox version: 1.20.0
* Symbols: unstripped; the executable contains debug information.

## Source

* Archive: `busybox-1.20.0-modified-simpleide-nommu-monotonicsyscall-oldkernel-src.tar.bz2`
* Reference: https://busybox.net/oldnews.html (BusyBox 1.20.0)
* Provenance: modified source. The archive name records NOMMU, monotonic
  syscall, and old-kernel changes; no more detailed patch inventory is
  documented here.

Extract this archive with `tar -xjf <archive> -C <destination>`. See the root
`README.txt` for complete extraction notes and `LICENSE.txt` for the
BusyBox GPL-2.0-only license.
