/**
 * Interpreter-backed language toolchains.
 *
 * <p>
 * Languages in this package extend
 * {@link io.simpleide.language.interpreter.AbstractInterpreterLanguage} to execute a
 * source file directly with an external interpreter. The package provides JavaScript
 * through {@code node} and Python 3 through {@code python3} or {@code python} on Windows.
 * {@link io.simpleide.language.interpreter.InterpreterRequiredFile} reports the
 * interpreter command a language needs during toolchain discovery.
 *
 * <p>
 * See docs/reference/LANGUAGES.md for the interpreter requirements.
 *
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * <p>
 * The interpreter reads the source file directly and stays alive while the program runs.
 * Standard input and output flow through the shared queues, and the language is
 * registered only when its interpreter command is present on the PATH.
 *
 */
package io.simpleide.language.interpreter;
