package io.simpleide.thread.nodes.runnable;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.Node;
import io.simpleide.thread.nodes.NodePattern;

public class RunnableNode extends Node {

    public RunnableNode(NodePattern superNode, NodePattern actual) {
        super(superNode, actual);
    }

    public RunnableNode(NodePattern superNode, Runnable onStart) {
        super(superNode, onStart);
    }

    public RunnableNode(NodePattern superNode, Runnable onStart, Runnable onClose) {
        super(superNode, onStart, onClose);
    }

    public RunnableNode(NodePattern actual) {
        super(ThreadMaster.getInstance().getROOT(), actual);
    }

    public RunnableNode(Runnable onStart) {
        super(ThreadMaster.getInstance().getROOT(), onStart);
    }

    public RunnableNode(Runnable onStart, Runnable onClose) {
        super(ThreadMaster.getInstance().getROOT(), onStart, onClose);
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
        return this.superNode.level() + 1;
    }

    @Override
    public boolean isEDT() {
        return false;
    }

}
