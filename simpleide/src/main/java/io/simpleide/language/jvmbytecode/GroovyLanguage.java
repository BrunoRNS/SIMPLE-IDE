package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;

public final class GroovyLanguage extends AbstractBytecodeLanguage {

    public GroovyLanguage(Path filePath) {
        super(filePath, "Groovy", "groovy");
    }
}
