/**
 * Shared helpers for reading source and resolving runners.
 *
 * <p>
 * {@link io.simplebytecode.compiler.utils.FileUtil} reads source files as strict UTF-8
 * text and rejects directories and malformed input, so a runner never receives a
 * partially decoded program. {@link io.simplebytecode.compiler.utils.MatchExtension} owns
 * the extension-to-runner table used by {@link io.simplebytecode.compiler.Runner}; adding
 * a language means adding a runner class and one entry to this table.
 *
 * <p>
 * Both helpers are stateless and safe to call from any thread. They perform blocking file
 * I/O in the calling thread and do not cache file contents.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 */
package io.simplebytecode.compiler.utils;
