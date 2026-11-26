package io.simpleide.frame.popup.utils;

/**
 * Error categories displayed by {@link io.simpleide.frame.popup.ErrorPopUP}.
 *
 * <p>
 * Each constant carries the human-readable title used for the dialog.
 *
 * <p>
 * Covered by {@code ErrorTypePopUpTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/popups.md">docs/api/ui/popups.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public enum ErrorTypePopUp {

	/** A runtime failure while executing a program. */
	RUNTIME_ERROR("Runtime error"),

	/** A failure during compilation. */
	COMPILE_ERROR("Compile error"),

	/** A failure during linking. */
	LINKER_ERROR("Linker error"),

	/** An unexpected failure inside the IDE. */
	INTERNAL_ERROR("Internal error"),

	/** An unrecoverable application failure. */
	FATAL_ERROR("Fatal error");

	private final String error;

	ErrorTypePopUp(String error) {
		this.error = error;
	}

	@Override
	public String toString() {
		return error;
	}

}
