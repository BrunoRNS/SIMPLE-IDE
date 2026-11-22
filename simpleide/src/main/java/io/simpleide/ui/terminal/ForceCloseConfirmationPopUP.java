package io.simpleide.ui.terminal;

import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import io.simpleide.frame.popup.WarningPopUP;

/**
 * Warning dialog that asks whether a running terminal process should be forced to exit.
 *
 * <p>
 * The dialog offers "Force exit" and "Cancel", defaults to cancel, and reports the answer
 * to a callback when it closes.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class ForceCloseConfirmationPopUP extends WarningPopUP {

	private static final String FORCE_EXIT = "Force exit";

	private static final String CANCEL = "Cancel";

	private final Consumer<Boolean> onAnswer;

	/**
	 * Creates the confirmation dialog.
	 * @param parent component that owns the dialog
	 * @param onAnswer callback receiving {@code true} for "Force exit" and {@code false}
	 * for "Cancel"
	 */
	public ForceCloseConfirmationPopUP(JComponent parent, Consumer<Boolean> onAnswer) {
		super("Close terminal", "Force the selected BusyBox process to exit?", parent, CANCEL);
		this.onAnswer = Objects.requireNonNull(onAnswer);
		jp.setOptions(new Object[] { FORCE_EXIT, CANCEL });
		jp.setOptionType(JOptionPane.YES_NO_OPTION);
		jp.setInitialValue(CANCEL);
	}

	@Override
	protected void onDialogClosed() {
		onAnswer.accept(FORCE_EXIT.equals(jp.getValue()));
	}

}
