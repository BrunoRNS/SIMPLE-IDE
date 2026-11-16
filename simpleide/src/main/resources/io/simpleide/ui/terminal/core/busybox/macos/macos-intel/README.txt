BusyBox for macOS Intel
=======================

Binary: bin/busybox
Version: BusyBox 1.20.0.git (built 2026-11-06)
Format: Mach-O 64-bit x86_64
Source: busybox-macos-intel-simpleide-src.tar.bz2
Upstream base: https://github.com/daliworks/busybox-osx/
License: GPLv2 (see LICENSE.txt)

Source provenance
-----------------

The source is a macOS Intel port based on BusyBox 1.20.0. The "simpleide"
marker in the archive name means it contains Simple IDE-specific patches for
the embedded terminal.

Build and packaging notes
-------------------------

Simple IDE extracts the Mach-O binary from the fat JAR, marks it executable, and
runs it from a temporary directory. The binary is not signed or notarized; it is
started directly by the same user that runs Simple IDE.

Upstream project: https://busybox.net/
