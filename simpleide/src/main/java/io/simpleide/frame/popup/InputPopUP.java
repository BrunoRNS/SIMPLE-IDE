package io.simpleide.frame.popup;

import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

/**
 * Information dialog that requests a single line of text.
 *
 * <p>
 * When the dialog closes, the callback receives the entered text or {@code null} when the
 * dialog was dismissed.
 *
 * <p>
 * Covered by {@code InputPopUPTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 */
public class InputPopUP extends InfoPopUP {

	private final Consumer<String> onInput;

	/**
	 * Creates a text input dialog.
	 * @param title dialog title
	 * @param message message displayed above the input field
	 * @param parent parent component used to position the dialog, or {@code null}
	 * @param onInput callback invoked with the entered text, or {@code null} when
	 * dismissed
	 */
	public InputPopUP(String title, String message, JComponent parent, Consumer<String> onInput) {
		super(title, message, parent, "Ok");
		this.onInput = onInput;
		jp.setWantsInput(true);
	}

	/**
	 * Reads the entered value and forwards it to the callback.
	 */
	@Override
	protected void onDialogClosed() {
		Object input = jp.getInputValue();
		boolean dismissed = jp.getValue() == JOptionPane.UNINITIALIZED_VALUE
				|| input == JOptionPane.UNINITIALIZED_VALUE;
		onInput.accept(!dismissed && input instanceof String value ? value : null);
	}

}
