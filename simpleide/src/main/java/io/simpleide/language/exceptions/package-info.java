/**
 * Exceptions raised by the language process lifecycle.
 *
 * <p>
 * {@link io.simpleide.language.exceptions.ProcessAlreadyExistsException} reports an
 * attempt to start a language that already has a live process, and
 * {@link io.simpleide.language.exceptions.ProcessFatalException} wraps startup,
 * interrupt, and forced-stop failures.
 *
 * <p>
 * See the error section of docs/api/languages.md for the expected failure modes.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * <p>
 * ProcessAlreadyExistsException is recoverable: stop the existing process before starting
 * a new one. ProcessFatalException means the process could not be started, was
 * interrupted or could not be force-stopped, and usually needs user attention. Both
 * exceptions preserve the original cause.
 *
 */
package io.simpleide.language.exceptions;
