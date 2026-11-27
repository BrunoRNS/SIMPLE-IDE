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
 * @deprecated since v1.1.0, use ThreadTree instead.
 * @author BrunoRNS
 * @since v1.0.0beta
 */
@Deprecated
public interface Tree {

	/**
	 * Adds a node to the tree.
	 * @param node node to register
	 */
	@Deprecated
	public void addNode(NodePattern node);

	/**
	 * Removes a node and all of its descendants from the tree.
	 * @param node node to remove
	 */
	@Deprecated
	public void removeNodeCascade(NodePattern node);

	/**
	 * Returns a snapshot of the current tree.
	 * @return an unmodifiable map from node identifier to tree node
	 */
	@Deprecated
	public Map<BigInteger, TreeNode> showTree();

}
