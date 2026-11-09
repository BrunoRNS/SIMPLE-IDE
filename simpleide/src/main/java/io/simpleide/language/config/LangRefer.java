package io.simpleide.language.config;

import io.simpleide.language.AbstractLanguage;

public class LangRefer {

    protected Class<? extends AbstractLanguage> langClass;

    public LangRefer(Class<? extends AbstractLanguage> langClass) {
        this.langClass = langClass;
    }

    public void setLangClass(Class<? extends AbstractLanguage> langClass) {
        this.langClass = langClass;
    }

    public Class<? extends AbstractLanguage> getLangClass() {
        return this.langClass;
    }
    
}
