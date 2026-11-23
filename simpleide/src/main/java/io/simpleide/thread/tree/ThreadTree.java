package io.simpleide.thread.tree;

import java.math.BigInteger;
import java.util.Map;

import io.simpleide.thread.nodes.NodePattern;

/**
 * Minimal contract for the thread tree.
 *
 * <p>
 * Implementations add and remove nodes and expose a snapshot of the tree keyed by node
 * identifier.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public interface ThreadTree {

	/**
	 * Adds a node to the tree.
	 * @param node node to register
	 */
	public void addNode(NodePattern node);

	/**
	 * Removes a node and all of its descendants from the tree.
	 * @param node node to remove
	 */
	public void removeNodeCascade(NodePattern node);

	/**
	 * Returns a snapshot of the current tree.
	 * @return an unmodifiable map from node identifier to tree node
	 */
	public Map<BigInteger, TreeNode> showTree();

}
