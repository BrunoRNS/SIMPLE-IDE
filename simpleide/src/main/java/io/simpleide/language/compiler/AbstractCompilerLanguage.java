package io.simpleide.language.compiler;

import java.nio.file.Path;

import io.simpleide.language.AbstractLanguage;

public abstract class AbstractCompilerLanguage extends AbstractLanguage {

    protected AbstractCompilerLanguage() {
        super();
    }

    protected AbstractCompilerLanguage(Path file, String friendlyName, String languageConstant) {
        super();
    }

    public abstract String getCompiler();
    public abstract String getAssembler();
    public abstract String getLinker();

}
