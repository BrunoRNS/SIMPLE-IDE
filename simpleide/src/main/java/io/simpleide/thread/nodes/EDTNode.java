package io.simpleide.thread.nodes;

import io.simpleide.thread.ThreadMaster;

public class EDTNode extends Node {

    public EDTNode(NodePattern actual) {
        super(ThreadMaster.ROOT, actual);
    }

    public EDTNode(Runnable onStart) {
        super(ThreadMaster.ROOT, onStart);
    }

    public EDTNode(Runnable onStart, Runnable onClose) {
        super(ThreadMaster.ROOT, onStart, onClose);
    }


    @Override
    public Runnable onStart() {
        return this.start;
    }

    @Override
    public Runnable onClose() {
        return this.close;
    }

    @Override
    public Integer level() {
        return 1;
    }
    
}
