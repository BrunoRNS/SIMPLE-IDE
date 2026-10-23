package io.simpleide.language.exceptions;

public final class ProcessRuntimeException
extends RuntimeException
{

    protected volatile Integer exitCode = null;

    public ProcessRuntimeException(
        String message,
        int exitCode
    ) {
        super(message);
        this.exitCode = exitCode;
    }

    public Integer getActualProcess() {
        return this.exitCode;
    }
    
}

