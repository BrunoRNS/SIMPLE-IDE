/**
 * Compile-then-run language toolchains.
 *
 * <p>
 * Languages in this package extend
 * {@link io.simpleide.language.compiler.AbstractCompilerLanguage} to build a source file
 * into a temporary executable and then run it. The available implementations wrap the C,
 * C++, C#, Go, and Rust toolchains.
 * {@link io.simpleide.language.compiler.CompilerRequiredFiles} reports the external
 * commands a compiler language needs so that language setup can verify they are present
 * on the system path.
 *
 * <p>
 * See docs/api/languages.md and docs/reference/LANGUAGES.md for command details.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * <p>
 * Compilation and execution run as external processes. The compiled artifact is temporary
 * and is cleaned up when the process finishes. A language is registered only when its
 * required compiler commands are present on the PATH, so registering it is safe on
 * systems without the toolchain.
 *
 */
package io.simpleide.language.compiler;
