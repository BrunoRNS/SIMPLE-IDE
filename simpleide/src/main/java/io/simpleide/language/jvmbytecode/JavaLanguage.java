package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;

public final class JavaLanguage extends AbstractBytecodeLanguage {

    public JavaLanguage(Path filePath) {
        super(filePath, "Java", "java");
    }
}
