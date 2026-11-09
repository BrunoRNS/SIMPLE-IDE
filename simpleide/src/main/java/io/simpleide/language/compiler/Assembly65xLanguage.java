package io.simpleide.language.compiler;

import java.nio.file.Path;

public class Assembly65xLanguage extends AbstractCompilerLanguage {

    public Assembly65xLanguage(Path filePath, String friendlyName, String languageConstant) {
        super();
    }

    @Override
    public String getCompiler() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getAssembler() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getLinker() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
