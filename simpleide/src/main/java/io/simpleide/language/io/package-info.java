/**
 * Shared input and output queues for running language processes.
 *
 * <p>
 * {@link io.simpleide.language.io.Input} carries lines sent by the IDE to a program, and
 * {@link io.simpleide.language.io.Output} carries lines published by the program to the
 * IDE. Both queues are thread-safe and are intended for one interactive program at a
 * time. The {@code holder} subpackage exposes the process-wide singleton instances.
 *
 * <p>
 * See docs/api/languages.md for examples that read program output and send input.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * <p>
 * The queues are bounded. A writer can block or fail when a program does not consume its
 * input, so send input from a background task rather than the EDT. Only one interactive
 * program should own the shared queues at a time; running two programs makes their output
 * indistinguishable.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
package io.simpleide.language.io;
