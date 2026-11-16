package io.simpleide.language.io.holder;

import io.simpleide.language.io.Input;

/**
 * Holds the process-wide input queue.
 *
 * <p>
 * The single {@link #INSTANCE} is shared by language runners and is exposed through
 * {@link Input#getInstance()}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class InputHolder {

	/**
	 * The shared input queue used by the language runner.
	 */
	public static final Input INSTANCE = new Input();

}
