/**
 * Stable entry points for embedding Simple IDE components in another application.
 *
 * <p>
 * The facades in this package are intentionally thin. They initialize the shared
 * {@link io.simpleide.thread.ThreadMaster}, install the available language toolchains,
 * and expose the editor, language, LSP and terminal components without requiring callers
 * to recreate the application frame.
 *
 * <p>
 * Each facade is independent when possible. For example, {@link io.simpleide.api.Editors}
 * does not start the thread manager or install language toolchains; it only creates
 * RSyntaxTextArea-based editors.
 *
 * <p>
 * The key types are {@link io.simpleide.api.Editors} for standalone editors,
 * {@link io.simpleide.api.SimpleEditor} for an embeddable editor tab with optional LSP,
 * {@link io.simpleide.api.Threads} for managed execution,
 * {@link io.simpleide.api.Languages} for language discovery and execution,
 * {@link io.simpleide.api.LanguageServers} for LSP connections,
 * {@link io.simpleide.api.Debugger} for debug sessions,
 * {@link io.simpleide.api.Terminals} for embedded terminals and
 * {@link io.simpleide.api.SimpleIDE} as a convenience entry point.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/README.md">docs/api/README.md</a>
 */
package io.simpleide.api;
