package io.simpleide.thread.nodes;

public class RunnableNode extends Node {

    public RunnableNode(NodePattern superNode, NodePattern actual) {
        super(superNode, actual);
    }

    @Override
    public Runnable onStart() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Runnable onClose() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Integer level() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
