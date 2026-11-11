package io.simpleide.language.compiler;

import java.util.ArrayList;
import java.util.List;

public class CompilerRequiredFiles {

    public static List<String> getRequiredFilesFromCompiler(
        Class<? extends AbstractCompilerLanguage> c
    ) {
        if (c == null) {
            throw new IllegalArgumentException("Compiler class must not be null.");
        }

        try {
            AbstractCompilerLanguage language = c.getDeclaredConstructor().newInstance();
            List<String> required = new ArrayList<>(4);
            addIfPresent(required, language.getCompiler());
            addIfPresent(required, language.getAssembler());
            addIfPresent(required, language.getLinker());
            addIfPresent(required, language.getRuntime());
            return List.copyOf(required);
        } catch (ReflectiveOperationException | SecurityException exception) {
            throw new IllegalStateException("Could not inspect compiler requirements for " + c.getName(), exception);
        }
    }

    private static void addIfPresent(List<String> required, String command) {
        if (command != null && !command.isBlank() && !required.contains(command)) {
            required.add(command);
        }
    }
}
