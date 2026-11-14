package io.simpleide.thread.nodes.record;

import io.simpleide.thread.nodes.NodePattern;

/**
 * Creates {@link NodeRecord} snapshots from thread tree patterns.
 *
 * <p>
 * Covered by {@code NodeRecordFactoryTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class NodeRecordFactory {

	/**
	 * Captures the current state of a node.
	 * @param node pattern to snapshot
	 * @return a record with the node's actions, level and identifier
	 */
	public static NodeRecord create(NodePattern node) {
		return new NodeRecord(node.onStart(), node.onClose(), node.level(), node.getId());
	}

}
