package io.simpleide.thread.tree;

import io.simpleide.thread.nodes.Node;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.validation.NodeRegistrationValidation;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe registry of thread tree nodes keyed by identifier.
 *
 * <p>
 * Registration validates that a node's level is exactly one greater than its parent's
 * level, that managed nodes name a registered parent, and that the root stays at level
 * zero. Removal collects the whole subtree so the scheduler can cancel the corresponding
 * tasks. Traversal helpers return identifiers in breadth-first, pre-order, in-order or
 * post-order sequence.
 *
 * <p>
 * Covered by {@code ThreadTreeRegistryTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class ThreadTreeRegistry implements Tree {

	private final TreeNode root;

	private final Map<BigInteger, TreeNode> nodes = new LinkedHashMap<>();

	/**
	 * Creates a registry with the supplied root node.
	 * @param root root pattern, which must have level zero
	 */
	public ThreadTreeRegistry(NodePattern root) {
		NodeRegistrationValidation.validateRoot(root);
		this.root = new TreeNode(root);
		nodes.put(root.getId(), this.root);
	}

	@Override
	public synchronized void addNode(NodePattern node) {
		NodeRegistrationValidation.INSTANCE.validate(node);
		BigInteger id = node.getId();
		TreeNode existing = nodes.get(id);
		if (existing != null) {
			if (existing.getNode() != node) {
				throw new IllegalStateException("A different node already uses ID " + id + ".");
			}
			return;
		}

		Integer level = node.level();
		TreeNode parent = findParentFor(node, level);
		if (parent == null || parent.getNode().level() == null || level != parent.getNode().level() + 1) {
			throw new IllegalArgumentException("Node level must be exactly one greater than its parent.");
		}

		TreeNode treeNode = new TreeNode(node);
		parent.addChild(treeNode);
		nodes.put(id, treeNode);
	}

	@Override
	public synchronized void removeNodeCascade(NodePattern node) {
		removeAndCollect(node);
	}

	/**
	 * Removes a node and its descendants and returns every removed pattern.
	 * @param node node to remove
	 * @return the removed patterns, including the supplied node when present
	 */
	public synchronized List<NodePattern> removeNodeCascadeAndCollect(NodePattern node) {
		return removeAndCollect(node);
	}

	@Override
	public synchronized Map<BigInteger, TreeNode> showTree() {
		Map<BigInteger, TreeNode> snapshot = new LinkedHashMap<>();
		TreeNode snapshotRoot = root.snapshot();
		LinkedList<TreeNode> queue = new LinkedList<>();
		queue.add(snapshotRoot);
		while (!queue.isEmpty()) {
			TreeNode node = queue.removeFirst();
			snapshot.put(node.getId(), node);
			queue.addAll(node.getChildren());
		}
		return Collections.unmodifiableMap(snapshot);
	}

	/**
	 * Returns a snapshot of the root entry.
	 * @return an independent copy of the root and its subtree
	 */
	public synchronized TreeNode getRoot() {
		return root.snapshot();
	}

	/**
	 * Returns every registered pattern in breadth-first order.
	 * @return the patterns starting at the root
	 */
	public synchronized List<NodePattern> breadthFirstNodes() {
		List<NodePattern> result = new ArrayList<>();
		LinkedList<TreeNode> queue = new LinkedList<>();
		queue.add(root);
		while (!queue.isEmpty()) {
			TreeNode current = queue.removeFirst();
			result.add(current.getNode());
			queue.addAll(current.getChildren());
		}
		return result;
	}

	/**
	 * Returns the number of registered nodes, including the root.
	 * @return the node count
	 */
	public synchronized int size() {
		return nodes.size();
	}

	/**
	 * Returns whether the exact node instance is registered.
	 * @param node node to look up, may be {@code null}
	 * @return {@code true} when the instance is currently registered
	 */
	public synchronized boolean contains(NodePattern node) {
		if (node == null) {
			return false;
		}
		TreeNode registeredNode = nodes.get(node.getId());
		return registeredNode != null && registeredNode.getNode() == node;
	}

	/**
	 * Appends the identifiers of a subtree in breadth-first order.
	 * @param node subtree root
	 * @param result list that receives the identifiers
	 * @return the supplied list
	 */
	public LinkedList<BigInteger> bfs(TreeNode node, LinkedList<BigInteger> result) {
		if (node == null || result == null) {
			throw new IllegalArgumentException("Node and result must not be null.");
		}
		LinkedList<TreeNode> queue = new LinkedList<>();
		queue.add(node);
		while (!queue.isEmpty()) {
			TreeNode current = queue.poll();
			result.add(current.getId());
			queue.addAll(current.getChildren());
		}
		return result;
	}

	/**
	 * Appends the identifiers of a subtree in pre-order.
	 * @param node subtree root
	 * @param result list that receives the identifiers
	 * @return the supplied list
	 */
	public LinkedList<BigInteger> dfsPreorder(TreeNode node, LinkedList<BigInteger> result) {
		if (node == null || result == null) {
			throw new IllegalArgumentException("Node and result must not be null.");
		}
		result.add(node.getId());
		for (TreeNode child : node.getChildren()) {
			dfsPreorder(child, result);
		}
		return result;
	}

	/**
	 * Appends the identifiers of a subtree in in-order.
	 * @param node subtree root
	 * @param result list that receives the identifiers
	 * @return the supplied list
	 */
	public LinkedList<BigInteger> dfsInorder(TreeNode node, LinkedList<BigInteger> result) {
		if (node == null || result == null) {
			throw new IllegalArgumentException("Node and result must not be null.");
		}
		if (node.hasChildren()) {
			dfsInorder(node.getChildren().get(0), result);
		}
		result.add(node.getId());
		for (int i = 1; i < node.getChildren().size(); i++) {
			dfsInorder(node.getChildren().get(i), result);
		}
		return result;
	}

	/**
	 * Appends the identifiers of a subtree in post-order.
	 * @param node subtree root
	 * @param result list that receives the identifiers
	 * @return the supplied list
	 */
	public LinkedList<BigInteger> dfsPostorder(TreeNode node, LinkedList<BigInteger> result) {
		if (node == null || result == null) {
			throw new IllegalArgumentException("Node and result must not be null.");
		}
		for (TreeNode child : node.getChildren()) {
			dfsPostorder(child, result);
		}
		result.add(node.getId());
		return result;
	}

	private List<NodePattern> removeAndCollect(NodePattern node) {
		if (node == null) {
			throw new IllegalArgumentException("Node must not be null.");
		}
		BigInteger id = node.getId();
		TreeNode treeNode = nodes.get(id);
		if (treeNode == null || treeNode.getNode() != node) {
			return List.of();
		}
		if (treeNode == root) {
			throw new IllegalArgumentException("The thread tree root cannot be removed.");
		}
		List<TreeNode> removed = new ArrayList<>();
		collectSubtree(treeNode, removed);
		TreeNode parent = findParent(root, treeNode);
		if (parent != null) {
			parent.removeChild(treeNode);
		}
		List<NodePattern> removedNodes = new ArrayList<>(removed.size());
		for (TreeNode removedNode : removed) {
			nodes.remove(removedNode.getId());
			removedNodes.add(removedNode.getNode());
		}
		return removedNodes;
	}

	private TreeNode findParentFor(NodePattern node, int level) {
		if (node instanceof Node managedNode) {
			BigInteger parentId = managedNode.getSuperNode().getId();
			TreeNode parent = nodes.get(parentId);
			if (parent == null) {
				throw new IllegalArgumentException("The node's parent must be registered first.");
			}
			return parent;
		}
		if (level == NodeRegistrationValidation.FIRST_NODE_LEVEL) {
			return root;
		}
		TreeNode match = null;
		for (TreeNode candidate : nodes.values()) {
			if (candidate.getNode().level() != null && candidate.getNode().level() == level - 1) {
				if (match != null) {
					throw new IllegalArgumentException(
							"A non-Node NodePattern at level " + level + " needs an explicit parent.");
				}
				match = candidate;
			}
		}
		return match;
	}

	private static void collectSubtree(TreeNode node, List<TreeNode> result) {
		result.add(node);
		for (TreeNode child : node.getChildren()) {
			collectSubtree(child, result);
		}
	}

	private static TreeNode findParent(TreeNode parent, TreeNode target) {
		if (parent.getChildren().contains(target)) {
			return parent;
		}
		for (TreeNode child : parent.getChildren()) {
			TreeNode found = findParent(child, target);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

}
