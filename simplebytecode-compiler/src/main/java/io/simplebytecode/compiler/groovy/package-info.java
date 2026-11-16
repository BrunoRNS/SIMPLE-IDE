/**
 * Groovy script execution support.
 *
 * <p>
 * {@link io.simplebytecode.compiler.groovy.GroovyRunner} parses Groovy source with the
 * bundled {@link groovy.lang.GroovyShell} and runs the resulting script in the current
 * JVM. The runner is selected for the {@code .groovy}, {@code .gvy}, {@code .gy} and
 * {@code .gsh} extensions.
 *
 * <p>
 * Script output written to {@code System.out} or {@code System.err} follows the normal
 * process streams. The runner does not redirect standard input, so interactive scripts
 * depend on the host process stdin. Compilation and evaluation failures are reported as
 * {@link IllegalArgumentException}, and a missing Groovy runtime is reported as
 * {@link IllegalStateException}.
 *
 * <p>
 * The Groovy runtime is loaded once and cannot be unloaded. Execution runs on the
 * caller's thread; the IDE starts the runner on a managed background node so the Swing
 * Event Dispatch Thread never blocks.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 */
package io.simplebytecode.compiler.groovy;
