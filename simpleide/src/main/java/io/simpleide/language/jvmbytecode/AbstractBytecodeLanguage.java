package io.simpleide.language.jvmbytecode;

import java.io.IOException;
import java.nio.file.Path;

import io.simpleide.language.AbstractLanguage;
import io.simpleide.utils.ResourceUtil;

public abstract class AbstractBytecodeLanguage extends AbstractLanguage {

    private static final String COMPILER_JAR_RESOURCE =
        "simplebytecode-compiler.jar";
    private static final Path COMPILER_JAR = getCompilerJarPath();

    protected AbstractBytecodeLanguage(Path filePath, String friendlyName, String languageConstant) {
        if (filePath == null) {
            LOGGER.error("[IDE] Cannot configure {} execution: source file path is null.", friendlyName);
            return;
        }
        if (COMPILER_JAR == null) {
            LOGGER.error("[IDE] Cannot configure {} execution: compiler JAR is unavailable.", friendlyName);
            return;
        }

        this.langFriendly = friendlyName;
        this.langConstant = languageConstant;
        Path sourceFile = filePath.toAbsolutePath().normalize();
        Path javaExecutable = Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
        );
        this.pb = new ProcessBuilder(
            javaExecutable.toString(),
            "-jar",
            COMPILER_JAR.toString(),
            sourceFile.toString()
        );
    }

    private static Path getCompilerJarPath() {
        try {
            return ResourceUtil.getResourcePath(AbstractBytecodeLanguage.class, COMPILER_JAR_RESOURCE);
        } catch (IOException exception) {
            LOGGER.error(
                "[IDE] Could not locate the embedded simplebytecode compiler JAR: {}",
                COMPILER_JAR_RESOURCE,
                exception
            );
            return null;
        }
    }
}