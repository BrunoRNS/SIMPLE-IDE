/**
 * Stable entry points for embedding Simple IDE components in another application.
 *
 * <p>The facades in this package are intentionally thin. They initialize the shared {@link
 * io.simpleide.thread.ThreadMaster}, install the available language toolchains, and expose the
 * editor, language, LSP and terminal components without requiring callers to recreate the
 * application frame.
 *
 * <p>Each facade is independent when possible. For example, {@link io.simpleide.api.Editors} does
 * not start the thread manager or install language toolchains; it only creates
 * RSyntaxTextArea-based editors.
 */
package io.simpleide.api;
