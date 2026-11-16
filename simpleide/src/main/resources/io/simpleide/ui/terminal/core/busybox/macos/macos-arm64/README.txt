BusyBox for macOS ARM64
=======================

Binary: bin/busybox
Version: BusyBox 1.20.0.git (built 2023-11-29)
Format: Mach-O 64-bit arm64
Source: busybox-macos-arm64-src.tar.bz2
Upstream base: https://github.com/kernelzeroday/busybox-macos-arm64
License: GPLv2 (see LICENSE.txt)

Source provenance
-----------------

The source is a macOS Apple Silicon port based on BusyBox 1.20.0. The archive
name does not contain the "simpleide" marker, so this resource uses the upstream
macOS ARM64 source tree used for the shipped binary.

Build and packaging notes
-------------------------

Simple IDE extracts the Mach-O binary from the fat JAR, marks it executable, and
runs it from a temporary directory. The binary is not signed or notarized; it is
started directly by the same user that runs Simple IDE.

Upstream project: https://busybox.net/
