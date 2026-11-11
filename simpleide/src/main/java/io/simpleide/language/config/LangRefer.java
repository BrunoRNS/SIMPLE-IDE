package io.simpleide.language.config;

import java.util.Objects;

import io.simpleide.language.AbstractLanguage;

public class LangRefer {

    protected Class<? extends AbstractLanguage> langClass;

    public LangRefer(Class<? extends AbstractLanguage> langClass) {
        this.langClass = Objects.requireNonNull(langClass, "Language class must not be null.");
    }

    public void setLangClass(Class<? extends AbstractLanguage> langClass) {
        this.langClass = Objects.requireNonNull(langClass, "Language class must not be null.");
    }

    public Class<? extends AbstractLanguage> getLangClass() {
        return this.langClass;
    }
    
}
