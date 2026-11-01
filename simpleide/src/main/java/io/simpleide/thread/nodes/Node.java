package io.simpleide.thread.nodes;

public abstract class Node implements NodePattern {

    protected Runnable start;
    protected Runnable close;
    protected Integer level;

    protected NodeRecord superNode;

    public NodeRecord getSuperNode() {
        return superNode;
    }

    public NodeRecord getNodeRecord() {
        return NodeRecordFactory.create(this);
    }

    public Node(NodePattern superNode, NodePattern actual) {
        this.superNode = NodeRecordFactory.create(superNode);
        this.start = actual.onStart();
        this.close = actual.onClose();
        this.level = actual.level();
    }

    public Node(NodePattern superNode, Runnable onStart) {
        this.superNode = NodeRecordFactory.create(superNode);
        this.start = onStart;
        this.close = null;
        this.level = null;
    }

    public Node(NodePattern superNode, Runnable onStart, Integer level) {
        this.superNode = NodeRecordFactory.create(superNode);
        this.start = onStart;
        this.close = null;
        this.level = level;
    }

    public Node(NodePattern superNode, Runnable onStart, Runnable onClose) {
        this.superNode = NodeRecordFactory.create(superNode);
        this.start = onStart;
        this.close = onClose;
        this.level = null;
    }

    public Node(NodePattern superNode, Runnable onStart, Runnable onClose, Integer level) {
        this.superNode = NodeRecordFactory.create(superNode);
        this.start = onStart;
        this.close = onClose;
        this.level = level;
    }
    
}

