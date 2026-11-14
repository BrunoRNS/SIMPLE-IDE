/**
 * Document tracking and language server lifecycle coordination.
 *
 * <p>
 * {@link io.simpleide.lsp.core.LanguageServerManager} detects a workspace root, shares
 * one language server per language and root, and publishes
 * {@link io.simpleide.lsp.LspStatus} updates. {@link io.simpleide.lsp.core.Document}
 * models the URI, language id and version sent to the server, while
 * {@link io.simpleide.lsp.core.DocumentManager} keeps the currently open documents.
 *
 * <p>
 * The manager shares one server per language and workspace root, serializes startup so
 * two documents cannot start the same server twice, and shuts it down when no document
 * remains. Startup may install a server and therefore block; it must not run on the EDT.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
package io.simpleide.lsp.core;
