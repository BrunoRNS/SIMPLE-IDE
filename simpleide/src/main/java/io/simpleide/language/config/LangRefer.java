package io.simpleide.language.config;

import io.simpleide.language.AbstractLanguage;
import java.util.Objects;

/**
 * Holds the language class associated with one or more file extensions.
 *
 * <p>
 * A reference is stored in {@link LangConfig} and resolves to an {@link AbstractLanguage}
 * subclass that exposes a public constructor taking a {@link java.nio.file.Path}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class LangRefer {

	protected Class<? extends AbstractLanguage> langClass;

	/**
	 * Creates a reference to a language class.
	 * @param langClass the language class, must not be {@code null}
	 * @throws NullPointerException if {@code langClass} is {@code null}
	 */
	public LangRefer(Class<? extends AbstractLanguage> langClass) {
		this.langClass = Objects.requireNonNull(langClass, "Language class must not be null.");
	}

	/**
	 * Replaces the referenced language class.
	 * @param langClass the new language class, must not be {@code null}
	 * @throws NullPointerException if {@code langClass} is {@code null}
	 */
	public void setLangClass(Class<? extends AbstractLanguage> langClass) {
		this.langClass = Objects.requireNonNull(langClass, "Language class must not be null.");
	}

	/**
	 * Returns the referenced language class.
	 * @return the language class
	 */
	public Class<? extends AbstractLanguage> getLangClass() {
		return this.langClass;
	}

}
