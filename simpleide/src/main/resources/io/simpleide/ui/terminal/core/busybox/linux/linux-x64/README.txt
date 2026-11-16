BusyBox for Linux x64
=====================

Binary: bin/busybox
Version: BusyBox 1.20.0
Format: ELF 64-bit LSB, x86-64, statically linked
Source: busybox-1.20.0-modified-simpleide-src.tar.bz2
License: GPLv2 (see LICENSE.txt)

Source provenance
-----------------

The source is based on BusyBox 1.20.0. The "simpleide" marker in the archive
name means the build contains Simple IDE-specific modifications used by the
embedded terminal. The binary is static-pie linked so it does not depend on the
host distribution's C library.

Build and packaging notes
-------------------------

Simple IDE extracts this binary at runtime, marks it executable, and removes it
when the terminal session ends. The corresponding source archive is bundled
next to the binary to keep the GPL source distribution complete.

Upstream project: https://busybox.net/
