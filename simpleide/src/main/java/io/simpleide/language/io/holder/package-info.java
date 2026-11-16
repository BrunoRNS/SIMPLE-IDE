/**
 * Process-wide holders for the shared language input and output queues.
 *
 * <p>
 * {@link io.simpleide.language.io.holder.InputHolder#INSTANCE} and
 * {@link io.simpleide.language.io.holder.OutputHolder#INSTANCE} are the singleton queues
 * used by the language runner. Application code should obtain them through
 * {@link io.simpleide.language.io.Input#getInstance()} and
 * {@link io.simpleide.language.io.Output#getInstance()} instead of referencing the
 * holders directly.
 *
 * <p>
 * See docs/api/languages.md for input and output usage examples.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * <p>
 * The holders exist to break initialization cycles between the queues and the language
 * runner. Reference the Input and Output facades instead of these fields. The instances
 * live for the lifetime of the JVM and are never replaced.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
package io.simpleide.language.io.holder;
