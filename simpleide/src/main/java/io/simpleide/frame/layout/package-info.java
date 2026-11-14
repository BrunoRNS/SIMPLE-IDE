/**
 * Swing layout helpers used by the application frame and welcome page.
 *
 * <p>
 * {@link io.simpleide.frame.layout.MainLayout} extends {@code GridBagLayout} with
 * convenience methods that add a component at explicit grid coordinates, span and
 * weights. It removes repeated constraint setup from the frame code.
 * {@link io.simpleide.frame.layout.SimpleLayout} extends {@code FlowLayout} with helpers
 * that add components aligned to the left, center or right.
 *
 * <p>
 * The helpers are thin wrappers: they do not perform layout caching, validation or
 * repainting beyond what the standard Swing layout managers do. Callers remain
 * responsible for calling {@code revalidate()} after changing the component tree, and
 * must create and mutate components on the Swing Event Dispatch Thread.
 *
 * <p>
 * These classes are application-internal. For a custom embedding window, use standard
 * Swing layout managers instead; the helpers are public because the application modules
 * need them, not as a stable API.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 */
package io.simpleide.frame.layout;
