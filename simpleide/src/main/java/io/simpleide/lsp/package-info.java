/**
 * Language Server Protocol integration for the editor.
 *
 * <p>
 * This package contains the shared status model used across the LSP subsystem.
 * {@link io.simpleide.lsp.LspStatus} reports installation and startup progress to the
 * user interface through its {@link io.simpleide.lsp.LspStatus.State} enum. The client,
 * core and download subpackages build on this model to discover, install, start and use
 * language servers.
 *
 * <p>
 * Status updates are produced by background download and startup work and delivered to UI
 * listeners as immutable snapshots. The status bar marshals them to the Swing Event
 * Dispatch Thread, so a listener may be called from any thread and must not assume the
 * EDT.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LSP.md">docs/reference/LSP.md</a>
 */
package io.simpleide.lsp;
