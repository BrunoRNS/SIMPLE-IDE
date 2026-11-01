package io.simpleide.thread.nodes;

public class NodeRecordFactory {

    public static NodeRecord create(NodePattern node) {
        return new NodeRecord(node.onStart(), node.onClose(), node.level());
    }
    
}
