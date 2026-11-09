package io.simpleide.language.config;

import java.util.HashMap;
import java.util.Map;

import io.simpleide.language.AbstractLanguage;

public class LangConfig {

    protected Map<String, LangRefer> languages;

    public LangConfig() {
        this.languages = new HashMap<>();
    }

    public Class<? extends AbstractLanguage> getLanguage(String extension) {
        LangRefer langRefer = this.languages.get(extension.toLowerCase());
        if (langRefer != null) {
            return langRefer.getLangClass();
        }
        return null;
    }

    public void addLanguage(Class<? extends AbstractLanguage> langClass, String... extensions) {
        
        LangRefer langRefer = new LangRefer(langClass);

        for (String extension : extensions) {
            this.languages.put(extension.toLowerCase(), langRefer);
        }

    }

    public Map<String, LangRefer> getLanguages() {
        return this.languages;
    }

    public LangConfig getInstance() {
        return LangConfigHolder.LANG_CONFIG;
    }

}
