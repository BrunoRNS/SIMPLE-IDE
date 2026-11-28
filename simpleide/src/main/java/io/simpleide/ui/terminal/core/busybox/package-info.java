/**
 * Extraction and platform selection for the bundled BusyBox executable.
 *
 * <p>
 * {@link io.simpleide.ui.terminal.core.busybox.BusyBoxExecutable} copies the embedded
 * binary into a temporary directory and deletes it on close.
 * {@link io.simpleide.ui.terminal.core.busybox.BusyBoxPlatform} maps a
 * {@link io.simpleide.language.config.LangPlatform} to the matching binary resource,
 * applies the x86 fallback for older 64-bit Windows versions and extracts the executable.
 *
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/third-
 * party/BUSYBOX.md">docs/third-party/BUSYBOX.md</a>
 */
package io.simpleide.ui.terminal.core.busybox;
