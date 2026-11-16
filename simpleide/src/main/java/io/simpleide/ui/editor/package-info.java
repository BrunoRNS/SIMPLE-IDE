/**
 * Embeddable editor components built on RSyntaxTextArea.
 *
 * <p>
 * {@link io.simpleide.ui.editor.Editor} is the text area itself, configured with code
 * folding, auto-indent, bracket matching and a file-type to syntax-style mapping.
 * {@link io.simpleide.ui.editor.EditorTab} wraps it in a tab with a search panel, line
 * numbers, save tracking and optional language support, while
 * {@link io.simpleide.ui.editor.EditorManager} owns the tab set, opens files, connects
 * language servers and performs save, close and go-to-definition actions.
 *
 * <p>
 * Language support is layered: {@link io.simpleide.ui.editor.LocalCompletionProviders}
 * supplies offline keyword completion, and LSP support replaces it once
 * {@link io.simpleide.ui.editor.EditorTab#connectLanguageServer} runs.
 * {@link io.simpleide.ui.editor.LspStatusBar} reports language server progress, and
 * {@link io.simpleide.ui.editor.EditorWelcomeScreen} is the empty-state page.
 *
 * @author BrunoRNS
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/editor.md">docs/api/editor.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/rsyntax.md">docs/api/rsyntax.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
package io.simpleide.ui.editor;
