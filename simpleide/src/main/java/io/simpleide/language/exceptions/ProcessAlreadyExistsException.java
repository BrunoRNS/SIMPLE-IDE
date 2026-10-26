package io.simpleide.language.exceptions;

public final class ProcessAlreadyExistsException 
extends RuntimeException
{

    private volatile Process actualProcess = null;

    public ProcessAlreadyExistsException(
        String message,
        Process actual
    ) {
        super(message);
        this.actualProcess = actual;
    }

    public Process getActualProcess() {
        return this.actualProcess;
    }
    
}
