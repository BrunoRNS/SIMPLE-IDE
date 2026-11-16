package io.simpleide.language.config;

/**
 * Holds the process-wide language configuration.
 *
 * <p>
 * The single {@link #LANG_CONFIG} instance is shared by language setup, the embedding
 * API, and editor actions.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class LangConfigHolder {

	/**
	 * The shared language registry used by the application.
	 */
	public static final LangConfig LANG_CONFIG = new LangConfig();

}
