package io.simpleide.language.io.holder;

import io.simpleide.language.io.Output;

/**
 * Holds the process-wide output queue.
 *
 * <p>
 * The single {@link #INSTANCE} is shared by language runners and is exposed through
 * {@link Output#getInstance()}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class OutputHolder {

	/**
	 * The shared output queue used by the language runner.
	 */
	public static final Output INSTANCE = new Output();

}
