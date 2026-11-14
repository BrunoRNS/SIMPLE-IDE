BusyBox for Linux ARM64
=======================

Binary: bin/busybox
Version: BusyBox 1.20.0
Format: ELF 64-bit LSB, ARM aarch64, statically linked
Source: busybox-1.20.0-arm64-simpleide-src.tar.bz2
License: GPLv2 (see LICENSE.txt)

Source provenance
-----------------

The source is based on BusyBox 1.20.0 with the Simple IDE ARM64 configuration.
The "simpleide" marker in the archive name means the source carries Simple
IDE-specific changes for the embedded terminal.

Build and packaging notes
-------------------------

The binary is static-pie linked. It is extracted to a temporary directory,
marked executable, and removed when the terminal session ends.

Upstream project: https://busybox.net/
