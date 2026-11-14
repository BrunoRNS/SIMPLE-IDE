BusyBox for Linux x86 (32-bit)
==============================

Binary: bin/busybox
Version: BusyBox 1.20.0
Format: ELF 32-bit LSB, Intel 80386, statically linked
Source: busybox-1.20.0-modified-simpleide-nommu-monotonicsyscall-oldkernel-src.tar.bz2
License: GPLv2 (see LICENSE.txt)

Source provenance
-----------------

The source is based on BusyBox 1.20.0. The "simpleide" marker in the archive
name means the build contains Simple IDE-specific modifications. The remaining
name records the compatibility options used for this build:

  - nommu                 NOMMU-compatible configuration.
  - monotonicsyscall      Monotonic syscall compatibility.
  - oldkernel             Compatibility with older Linux kernels.

These options are required so the 32-bit terminal binary remains compatible
with the widest possible range of Linux x86 systems.

Build and packaging notes
-------------------------

The binary is static-pie linked and stripped so it can run without shared
libraries from the host distribution. Simple IDE copies this resource to a
temporary directory and marks it executable before starting the terminal.

Upstream project: https://busybox.net/
