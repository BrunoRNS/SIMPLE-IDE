package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;

public final class KotlinLanguage extends AbstractBytecodeLanguage {

    public KotlinLanguage(Path filePath) {
        super(filePath, "Kotlin", "kotlin");
    }
}
