/**
 * JVM language execution through the embedded Simple IDE bytecode compiler.
 *
 * <p>
 * Languages in this package extend
 * {@link io.simpleide.language.jvmbytecode.AbstractBytecodeLanguage}, which launches the
 * bundled bytecode compiler with the current Java executable. The package provides Java,
 * Kotlin, and Groovy without requiring a separate compiler or interpreter on the system
 * path.
 *
 * <p>
 * See docs/reference/LANGUAGES.md for the toolchain requirements.
 *
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * <p>
 * Each run starts a separate Java process that launches the bundled bytecode compiler, so
 * the Kotlin and Groovy runtimes are not loaded into the IDE itself. Execution is
 * asynchronous and tracked by the thread manager, and the child process is stopped when
 * the language is stopped.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
package io.simpleide.language.jvmbytecode;
