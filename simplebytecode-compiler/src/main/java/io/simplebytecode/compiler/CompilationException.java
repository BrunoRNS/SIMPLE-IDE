package io.simplebytecode.compiler;

public class CompilationException extends Exception {

    private static final long serialVersionUID = 1L;

    public CompilationException(String message) {
        super(message);
    }

    public CompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
