/**
 * Language server registry, version resolution, installation and launch commands.
 *
 * <p>
 * {@link io.simpleide.lsp.dowload.LspManager} reads the bundled {@code registry.json},
 * filters entries by platform and language version, and builds the launch command.
 * {@link io.simpleide.lsp.dowload.LspReleaseResolver} refreshes release metadata from
 * trusted APIs, {@link io.simpleide.lsp.dowload.LspInstaller} downloads or installs a
 * server into {@code ~/.simpleide/lsp}, and {@link io.simpleide.lsp.dowload.LspServer}
 * with {@link io.simpleide.lsp.dowload.LspRegistry} model the registry. The package name
 * keeps the original {@code dowload} spelling for compatibility.
 *
 * <p>
 * Installation performs network and process I/O and must run off the Swing Event Dispatch
 * Thread. Downloads and release metadata are cached under {@code ~/.simpleide/lsp}; the
 * cache is bounded in time and is refreshed only when an entry is missing or expired.
 *
 * @author LuisOtavio13
 * @author luisotavio15
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LSP.md">docs/reference/LSP.md</a>
 */
package io.simpleide.lsp.dowload;
