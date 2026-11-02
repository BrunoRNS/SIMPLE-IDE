package io.simpleide.thread.tree;

import java.util.Map;

import io.simpleide.thread.nodes.NodePattern;

public interface Tree {

    public void addNode(NodePattern node);
    public void removeNodeCascade(NodePattern node);
    public Map<Integer, TreeNode> showTree();

}
