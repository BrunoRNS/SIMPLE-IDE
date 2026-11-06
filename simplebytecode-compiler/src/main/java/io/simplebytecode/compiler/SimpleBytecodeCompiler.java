package io.simplebytecode.compiler;

import java.nio.file.Path;

public final class SimpleBytecodeCompiler {

    public static void main(String[] args) {
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int execute(String... args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            System.err.println("ERROR: No file provided");
            return -1;
        }

        try {
            new Runner(Path.of(args[0])).run();
            return 0;
        } catch (UnsupportedOperationException exception) {
            System.err.println("ERROR: " + exception.getMessage());
            return -2;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
            System.err.println("ERROR: " + message);
            return -5;
        }
    }
}
