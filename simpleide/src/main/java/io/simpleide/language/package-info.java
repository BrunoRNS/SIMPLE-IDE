/**
 * Core language execution abstractions for Simple IDE.
 *
 * <p>
 * The package defines {@link io.simpleide.language.AbstractLanguage}, the shared base
 * class that runs configured {@link java.lang.ProcessBuilder} pipelines, publishes
 * process output to {@link io.simpleide.language.io.Output}, and forwards queued input
 * lines from {@link io.simpleide.language.io.Input} to the running program. Concrete
 * toolchains live in the {@code compiler}, {@code interpreter}, and {@code jvmbytecode}
 * subpackages and are registered through {@link io.simpleide.language.config.LangConfig}.
 *
 * <p>
 * See the language API guide for lifecycle, input/output handling, and error behaviour.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * <p>
 * A language instance owns an external process. Start it through the managed methods so
 * the thread manager tracks its lifetime; the process publishes output to the shared
 * queues and consumes queued input. Stop the process and close its streams before
 * discarding the instance, and never block the Swing Event Dispatch Thread while a
 * program runs.
 *
 */
package io.simpleide.language;
