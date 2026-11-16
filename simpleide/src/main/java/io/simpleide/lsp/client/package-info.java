/**
 * LSP4J client process management and editor integration.
 *
 * <p>
 * {@link io.simpleide.lsp.client.LspClient} starts a language server as a stdio
 * subprocess, performs the LSP handshake and forwards document and request events.
 * {@link io.simpleide.lsp.client.LspEditorSupport} binds one client to an editor and
 * wires diagnostics, completion and formatting. The remaining types implement completion
 * items, additional text edits, diagnostic parsing and the startup exception used when a
 * server exits before initialization.
 *
 * <p>
 * One client owns one server process and its LSP4J connection. Close the client to stop
 * the process and release its streams; requests completed after close fail. Reader and
 * request callbacks arrive on LSP4J threads, so editor updates are marshalled to the EDT.
 *
 * @author BrunoRNS
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
package io.simpleide.lsp.client;
