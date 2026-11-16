/**
 * Core entry points for the standalone Simple Bytecode compiler.
 *
 * <p>
 * The package maps a source file to the language runner that understands its extension
 * and provides both a programmatic and a command-line entry point.
 * {@link io.simplebytecode.compiler.Runner} resolves the extension through
 * {@link io.simplebytecode.compiler.utils.MatchExtension}, reflectively creates the
 * runner and executes it. {@link io.simplebytecode.compiler.RunnerInterface} is the
 * contract a runner implements: parse or compile in {@code prepare(String)} and run in
 * {@code run()}. {@link io.simplebytecode.compiler.SimpleBytecodeCompiler} is the
 * {@code main} class of the standalone artifact.
 *
 * <p>
 * Concrete runners live in {@code io.simplebytecode.compiler.classloader} for Java,
 * {@code groovy} for Groovy scripts and {@code kotlin} for Kotlin scripts. Shared file
 * and extension helpers live in {@code io.simplebytecode.compiler.utils}. The module has
 * no dependency on the IDE, the thread manager or Swing, so it can be used from any Java
 * 17 application or run as a separate process.
 *
 * <p>
 * A runner executes on the thread that calls {@code run()}, and its language runtime
 * stays loaded for the lifetime of the JVM. Long-running programs should therefore be
 * started on a background thread by the caller, and a process that needs to switch
 * language runtime versions should be restarted.
 *
 * @author BrunoRNS
 * @author luisotavio15
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 */
package io.simplebytecode.compiler;
