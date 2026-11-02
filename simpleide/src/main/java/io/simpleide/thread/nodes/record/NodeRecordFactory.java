package io.simpleide.thread.nodes.record;

import io.simpleide.thread.nodes.NodePattern;

public class NodeRecordFactory {

    public static NodeRecord create(NodePattern node) {
        return new NodeRecord(node.onStart(), node.onClose(), node.level(), node.getId());
    }
    
}
