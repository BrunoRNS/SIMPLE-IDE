package io.simpleide.language.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class ProcessFatalException 
extends UncheckedIOException
{

    private IOException cause = null;
    private Process pcause = null;

    public ProcessFatalException(
        String message,
        IOException cause
    ) {
        super(message, cause);
        this.cause = cause;
    }

    public ProcessFatalException(
        String message,
        Process pcause
    ) {
        super(message, new IOException(message));
        this.pcause = pcause;
    }

    public IOException getCause() {
        return this.cause;
    }

    public Process getProcessCause() {
        return this.pcause;
    }
    
}
