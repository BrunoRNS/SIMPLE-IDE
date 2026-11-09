package io.simpleide.language.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.simpleide.language.AbstractLanguage;

public class LangConfig {

    protected Map<String, LangRefer> languages;

    public LangConfig() {
        this.languages = new HashMap<>();
    }

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

    public AbstractLanguage createLanguage(String extension, Path file) {
        Objects.requireNonNull(file, "Source file path must not be null.");
        Class<? extends AbstractLanguage> languageClass = getLanguage(extension);
        if (languageClass == null) {
            return null;
        }

        try {
            Constructor<? extends AbstractLanguage> constructor = languageClass.getConstructor(Path.class);
            return constructor.newInstance(file);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Language constructor failed for " + languageClass.getName(),
                    exception.getCause()
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Language class must expose a public Path constructor: " + languageClass.getName(),
                    exception
            );
        }
    }

    public Map<String, LangRefer> getLanguages() {
        return Map.copyOf(this.languages);
    }

    public LangConfig getInstance() {
        return LangConfigHolder.LANG_CONFIG;
    }

}
