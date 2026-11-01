package io.simpleide.thread.nodes;

public interface NodePattern {

    public abstract Runnable onStart();
    public abstract Runnable onClose();

    public abstract Integer level();
    
}
