Simple IDE embedded BusyBox terminal resources
===============================================

This directory contains the BusyBox executables used by the embedded terminal.
The terminal extracts the binary that matches the current operating system and
CPU architecture, runs it from a temporary directory, and deletes it when the
terminal session ends.

The binaries are pre-downloaded and bundled inside the Simple IDE fat JAR. They
are not downloaded at runtime, so the terminal works without network access.

Directory layout
----------------

  linux/linux-x86/              Linux, 32-bit x86
  linux/linux-x64/              Linux, 64-bit x86
  linux/linux-arm64/            Linux, 64-bit ARM
  macos/macos-intel/            macOS, 64-bit Intel
  macos/macos-arm64/            macOS, Apple Silicon
  windows/windows-x86/          Windows, 32-bit x86
  windows/windows-x64-unicode/  Windows, 64-bit x86, Unicode build
  windows/windows-arm64/        Windows, 64-bit ARM

Each platform directory contains:

  bin/busybox or bin/busybox.exe   The runnable BusyBox binary.
  README.txt                       Provenance and platform-specific notes.
  LICENSE.txt                      The complete license that applies to the binary.
  *-src.tar.bz2                    The corresponding source archive.

Source archive naming
---------------------

  busybox-1.20.0-arm64-simpleide-src.tar.bz2
      Upstream BusyBox 1.20.0 with Simple IDE-specific patches.

  busybox-1.20.0-modified-simpleide-src.tar.bz2
      Upstream BusyBox 1.20.0 with Simple IDE-specific modifications.

  busybox-1.20.0-modified-simpleide-nommu-monotonicsyscall-oldkernel-src.tar.bz2
      Upstream BusyBox 1.20.0 with Simple IDE modifications for kernels and
      configurations that require NOMMU, monotonic syscalls, or old-kernel
      compatibility.

  busybox-macos-intel-simpleide-src.tar.bz2
      macOS Intel source with Simple IDE-specific patches.

  busybox-macos-arm64-src.tar.bz2
      macOS ARM64 source used for the Apple Silicon binary.

  busybox-w32-src.tar.bz2
      Source used for the Windows busybox-w32 binaries.

When "simpleide" appears in an archive name, the source was modified or patched
for Simple IDE. Archives without that marker are the upstream source used for
the build. Always keep the source archive next to the binary it produced.

License and source compliance
-----------------------------

BusyBox is distributed under the GNU General Public License version 2 (GPLv2).
The complete license text is in each platform's LICENSE.txt. The corresponding
source archive is bundled in the same directory, so recipients of Simple IDE
receive the source for the exact BusyBox family used by the terminal.

Simple IDE itself is licensed under the BSD 3-Clause License. The BusyBox
binaries remain GPLv2 software. See docs/third-party/BUSYBOX.md and
docs/THIRD_PARTY_LICENSES.md in the source repository for the full third-party
inventory.

Updating BusyBox
----------------

1. Replace bin/busybox (or bin/busybox.exe) for the platform.
2. Add the exact source archive to the same directory.
3. Replace LICENSE.txt when the upstream license changes.
4. Update VERSIONS.txt and the platform README.txt.
5. Do not change the directory layout: BusyBoxPlatform resolves these paths by
   platform and would otherwise fail to start the terminal.
6. Run the terminal tests:

       make test

Platform paths are declared in
simpleide/src/main/java/io/simpleide/ui/terminal/core/busybox/BusyBoxPlatform.java.
