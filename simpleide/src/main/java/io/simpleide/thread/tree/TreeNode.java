package io.simpleide.thread.tree;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import io.simpleide.thread.nodes.NodePattern;

/**
 * One entry in the thread tree, holding a node pattern and its children.
 *
 * <p>
 * Mutating operations are synchronized, and child lists are copied when returned so
 * callers never observe the internal list.
 *
 * <p>
 * Covered by {@code TreeNodeTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class TreeNode {

	private final BigInteger id;

	private final NodePattern node;

	private final LinkedList<TreeNode> children;

	/**
	 * Creates a tree entry for a node, using the node's own identifier.
	 * @param node pattern represented by this entry
	 */
	public TreeNode(NodePattern node) {
		this(node, node.getId());
	}

	private TreeNode(NodePattern node, BigInteger id) {
		this.node = node;
		this.id = id;
		this.children = new LinkedList<>();
	}

	/**
	 * Returns the identifier of this entry.
	 * @return the node identifier
	 */
	public BigInteger getId() {
		return id;
	}

	/**
	 * Returns the pattern represented by this entry.
	 * @return the node pattern
	 */
	public NodePattern getNode() {
		return node;
	}

	/**
	 * Returns an immutable snapshot of the direct children.
	 * @return the children in insertion order
	 */
	public synchronized List<TreeNode> getChildren() {
		return List.copyOf(children);
	}

	/**
	 * Adds a direct child.
	 * @param child child entry to append
	 */
	public synchronized void addChild(TreeNode child) {
		this.children.add(child);
	}

	/**
	 * Removes a direct child.
	 * @param child child entry to remove
	 */
	public synchronized void removeChild(TreeNode child) {
		this.children.remove(child);
	}

	/**
	 * Returns whether this entry has any children.
	 * @return {@code true} when at least one child is present
	 */
	public synchronized boolean hasChildren() {
		return !this.children.isEmpty();
	}

	/**
	 * Creates a deep copy of this entry and its subtree.
	 * @return an independent copy of the tree rooted at this entry
	 */
	public TreeNode snapshot() {
		TreeNode copy = new TreeNode(node, id);
		for (TreeNode child : getChildren()) {
			copy.addChild(child.snapshot());
		}
		return copy;
	}

}
