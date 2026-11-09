package io.simpleide.thread.tree;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import io.simpleide.thread.nodes.NodePattern;

public class TreeNode {

    private final BigInteger id;
    private final NodePattern node;
    private final LinkedList<TreeNode> children;

    public TreeNode(NodePattern node) {
        this(node, node.getId());
    }

    private TreeNode(NodePattern node, BigInteger id) {
        this.node = node;
        this.id = id;
        this.children = new LinkedList<>();
    }

    public BigInteger getId() {
        return id;
    }

    public NodePattern getNode() {
        return node;
    }

    public synchronized List<TreeNode> getChildren() {
        return List.copyOf(children);
    }

    public synchronized void addChild(TreeNode child) {
        this.children.add(child);
    }

    public synchronized void removeChild(TreeNode child) {
        this.children.remove(child);
    }

    public synchronized boolean hasChildren() {
        return !this.children.isEmpty();
    }

    public TreeNode snapshot() {
        TreeNode copy = new TreeNode(node, id);
        for (TreeNode child : getChildren()) {
            copy.addChild(child.snapshot());
        }
        return copy;
    }

}
