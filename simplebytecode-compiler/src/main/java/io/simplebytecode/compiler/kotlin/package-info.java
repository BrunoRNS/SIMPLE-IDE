/**
 * Kotlin script execution support.
 *
 * <p>
 * {@link io.simplebytecode.compiler.kotlin.KotlinRunner} compiles and evaluates Kotlin
 * scripts through the JSR-223 {@link javax.script.ScriptEngine} registered for the
 * {@code kts} extension. The runner is selected for the {@code .kt} and {@code .kts}
 * extensions.
 *
 * <p>
 * The Kotlin scripting engine is created once per runner instance. Compilation errors are
 * reported as {@link IllegalArgumentException}, a missing engine as
 * {@link IllegalStateException}, and evaluation failures are rethrown to the caller.
 * Standard output follows the host process streams; standard input is not redirected.
 *
 * <p>
 * The Kotlin compiler and scripting runtime stay loaded for the lifetime of the JVM.
 * Execution runs on the caller's thread, so the IDE starts Kotlin programs on a managed
 * background node instead of the Swing Event Dispatch Thread.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 */
package io.simplebytecode.compiler.kotlin;
