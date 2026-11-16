package io.simpleide.language.config;

import io.simpleide.language.AbstractLanguage;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of installed language implementations keyed by file extension.
 *
 * <p>
 * Extensions are stored in lower case. A language class may be registered for several
 * extensions, and {@link #createLanguage(String, Path)} constructs it reflectively
 * through a public {@link Path} constructor. Instances typically come from
 * {@link LangConfigHolder#LANG_CONFIG} and are populated by {@link LangSetup}.
 *
 * <p>
 * Covered by {@code LangConfigTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class LangConfig {

	protected Map<String, LangRefer> languages;

	public LangConfig() {
		this.languages = new HashMap<>();
	}

	/**
	 * Returns the language class registered for an extension.
	 * @param extension the file extension, optionally including the leading dot
	 * @return the registered language class, or {@code null} when the extension is
	 * unknown or {@code null}
	 */
	public Class<? extends AbstractLanguage> getLanguage(String extension) {
		if (extension == null) {
			return null;
		}
		LangRefer langRefer = this.languages.get(extension.toLowerCase(Locale.ROOT));
		if (langRefer != null) {
			return langRefer.getLangClass();
		}
		return null;
	}

	/**
	 * Registers a language class for one or more file extensions.
	 * @param langClass the language class to register
	 * @param extensions the file extensions handled by the language
	 * @throws NullPointerException if {@code langClass} or {@code extensions} is
	 * {@code null}
	 * @throws IllegalArgumentException if an extension is {@code null} or blank
	 */
	public void addLanguage(Class<? extends AbstractLanguage> langClass, String... extensions) {
		Objects.requireNonNull(langClass, "Language class must not be null.");
		Objects.requireNonNull(extensions, "Extensions must not be null.");
		LangRefer langRefer = new LangRefer(langClass);
		for (String extension : extensions) {
			if (extension == null || extension.isBlank()) {
				throw new IllegalArgumentException("Language extension must not be blank.");
			}
			this.languages.put(extension.toLowerCase(Locale.ROOT), langRefer);
		}
	}

	/**
	 * Creates a language instance for an extension and source file.
	 * @param extension the file extension used to look up the language
	 * @param file the source file passed to the language constructor
	 * @return the new language instance, or {@code null} when the extension is not
	 * registered
	 * @throws NullPointerException if {@code file} is {@code null}
	 * @throws IllegalStateException if the language constructor fails or is not
	 * accessible
	 */
	public AbstractLanguage createLanguage(String extension, Path file) {
		Objects.requireNonNull(file, "Source file path must not be null.");
		Class<? extends AbstractLanguage> languageClass = getLanguage(extension);
		if (languageClass == null) {
			return null;
		}

		try {
			Constructor<? extends AbstractLanguage> constructor = languageClass.getConstructor(Path.class);
			return constructor.newInstance(file);
		}
		catch (InvocationTargetException exception) {
			throw new IllegalStateException("Language constructor failed for " + languageClass.getName(),
					exception.getCause());
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(
					"Language class must expose a public Path constructor: " + languageClass.getName(), exception);
		}
	}

	/**
	 * Returns an immutable snapshot of the registered extensions.
	 * @return a map from extension to language reference
	 */
	public Map<String, LangRefer> getLanguages() {
		return Map.copyOf(this.languages);
	}

	/**
	 * Returns the shared language configuration instance.
	 * @return the application-wide {@link LangConfig}
	 */
	public LangConfig getInstance() {
		return LangConfigHolder.LANG_CONFIG;
	}

}
