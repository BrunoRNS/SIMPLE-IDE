/**
 * Supporting types for the application popups.
 *
 * <p>
 * {@link io.simpleide.frame.popup.utils.ErrorTypePopUp} enumerates the error categories
 * used by {@link io.simpleide.frame.popup.ErrorPopUP}: internal errors, language
 * problems, file problems and similar groups. The enum constant name (with underscores
 * replaced by spaces) supplies the dialog title, so a new category is available to every
 * caller as soon as the constant is added.
 *
 * <p>
 * The package depends only on the popup package and Swing. It has no mutable state and is
 * safe to reference from any thread; the dialog itself is still created on the EDT by
 * {@code ErrorPopUP}.
 *
 * @author LuisOtavio
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 */
package io.simpleide.frame.popup.utils;
