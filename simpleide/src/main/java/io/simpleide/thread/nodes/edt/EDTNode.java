package io.simpleide.thread.nodes.edt;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.Node;
import io.simpleide.thread.nodes.NodePattern;

public class EDTNode extends Node {

    public EDTNode() {
        super(ThreadMaster.ROOT);
    }

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

    @Override
    public boolean isEDT() {
        return true;
    }
    
}
