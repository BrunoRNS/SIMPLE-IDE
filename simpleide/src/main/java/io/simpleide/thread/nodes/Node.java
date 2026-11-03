package io.simpleide.thread.nodes;

import io.simpleide.thread.nodes.record.NodeRecord;
import io.simpleide.thread.nodes.record.NodeRecordFactory;

public abstract class Node implements NodePattern {

    protected Runnable start;
    protected Runnable close;
    protected Integer level;

    protected NodePattern superNode;

    public NodePattern getSuperNode() {
        return superNode;
    }

    public NodeRecord getNodeRecord() {
        return NodeRecordFactory.create(this);
    }

    public Node(NodePattern superNode, NodePattern actual) {
        this.superNode = superNode;
        this.start = actual.onStart();
        this.close = actual.onClose();
        Integer actualLevel = actual.level();
        this.level = actualLevel == null ? nextLevel(superNode) : actualLevel;
        this.getId();
    }

    public Node(NodePattern superNode) {
        this.superNode = superNode;
        this.start = null;
        this.close = null;
        this.level = nextLevel(superNode);
        this.getId();
    }

    public Node(NodePattern superNode, Runnable onStart) {
        this.superNode = superNode;
        this.start = onStart;
        this.close = null;
        this.level = nextLevel(superNode);
        this.getId();
    }

    public Node(NodePattern superNode, Runnable onStart, Integer level) {
        this.superNode = superNode;
        this.start = onStart;
        this.close = null;
        this.level = level == null ? nextLevel(superNode) : level;
        this.getId();
    }

    public Node(NodePattern superNode, Runnable onStart, Runnable onClose) {
        this.superNode = superNode;
        this.start = onStart;
        this.close = onClose;
        this.level = nextLevel(superNode);
        this.getId();
    }

    public Node(NodePattern superNode, Runnable onStart, Runnable onClose, Integer level) {
        this.superNode = superNode;
        this.start = onStart;
        this.close = onClose;
        this.level = level == null ? nextLevel(superNode) : level;
        this.getId();
    }

    private static Integer nextLevel(NodePattern parent) {
        Integer parentLevel = parent.level();
        if (parentLevel == null || parentLevel == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Parent must have a valid thread tree level.");
        }
        return parentLevel + 1;
    }
    
}
