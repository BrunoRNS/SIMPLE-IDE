/**
 * Managed modal dialog helpers used by the application and reusable components.
 *
 * <p>
 * {@link io.simpleide.frame.popup.PopUP} is the abstract base: it owns the
 * {@link javax.swing.JOptionPane}, a visibility flag and the lifecycle hooks.
 * {@link io.simpleide.frame.popup.InfoPopUP},
 * {@link io.simpleide.frame.popup.SuccessPopUP},
 * {@link io.simpleide.frame.popup.WarningPopUP} and
 * {@link io.simpleide.frame.popup.ErrorPopUP} provide the standard message variants,
 * while {@link io.simpleide.frame.popup.InputPopUP} adds a single-line input callback.
 *
 * <p>
 * A dialog is shown with {@code run()} or {@code startManaged()}. Both queue the dialog
 * on the Swing Event Dispatch Thread through the thread manager; {@code startManaged()}
 * additionally returns the {@code NodePattern} that tracks the dialog. Only one instance
 * of a popup may be visible at a time, and the visibility flag is reset when the dialog
 * closes, including on failure. Subclasses override {@code onDialogClosed()} to read the
 * selected option and notify a callback.
 *
 * <p>
 * The popups are modal: the EDT enters a nested event loop while the dialog is open.
 * Callers should not block a worker thread waiting for a result — pass a callback
 * instead. The dialogs are not headless-safe.
 *
 * @author BrunoRNS
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 */
package io.simpleide.frame.popup;
