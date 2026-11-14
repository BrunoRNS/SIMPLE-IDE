BusyBox for Windows x64 (Unicode)
=================================

Binary: bin/busybox.exe
Version: BusyBox v1.39.0-FRP-6354-g5f09fb446 (2026-10-27)
Format: PE32+ console executable, x86-64
Source: busybox-w32-src.tar.bz2
Upstream base: https://frippery.org/busybox/
License: GPLv2 (see LICENSE.txt)

Source provenance
-----------------

This is the Unicode busybox-w32 build used on Windows 10 and later. The source
archive is the same busybox-w32 tree used for the Windows x86 and Windows ARM64
binaries.

Platform selection
------------------

Simple IDE uses this binary on 64-bit Windows. On versions older than Windows 10
it falls back to the x86 binary. See BusyBoxPlatform.getCompatiblePlatform for
the exact rule.

Build and packaging notes
-------------------------

Simple IDE extracts this executable to a temporary directory and starts it as
the terminal shell. The binary is not installed system-wide.

Upstream project: https://busybox.net/ and https://frippery.org/busybox/
