/**
 * Utility helpers for the application frame.
 *
 * <p>
 * {@link io.simpleide.frame.utils.LoadIcon} loads icon resources from the classpath and
 * wraps a missing resource in a {@link java.util.MissingResourceException}. It is used by
 * frame widgets that need an {@link javax.swing.Icon} directly; code that needs a
 * filesystem {@link java.nio.file.Path} to a bundled resource should use
 * {@link io.simpleide.utils.ResourceUtil} instead.
 *
 * <p>
 * Resource loading performs blocking classpath I/O in the calling thread and is not
 * cached by this package. Load icons once during component construction rather than on
 * every paint pass, and construct the owning Swing components on the EDT.
 *
 * @author LuisOtavio
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
package io.simpleide.frame.utils;
