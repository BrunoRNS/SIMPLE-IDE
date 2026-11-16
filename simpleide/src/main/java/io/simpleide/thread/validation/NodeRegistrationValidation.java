package io.simpleide.thread.validation;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.edt.EDTNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * Validation rules applied before a node enters the thread tree.
 *
 * <p>
 * Every non-root node needs a positive level, {@code EDTNode} instances must run on the
 * EDT at level one, and {@code RunnableNode} instances must target a background worker.
 * The root must have level zero.
 *
 * <p>
 * Covered by {@code NodeRegistrationValidationTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class NodeRegistrationValidation implements Validation<NodePattern> {

	public static final int ROOT_LEVEL = 0;

	public static final int FIRST_NODE_LEVEL = 1;

	public static final int EDT_NODE_LEVEL = FIRST_NODE_LEVEL;

	public static final NodeRegistrationValidation INSTANCE = new NodeRegistrationValidation();

	private NodeRegistrationValidation() {
	}

	/**
	 * Validates a thread tree root.
	 * @param root candidate root pattern
	 * @throws IllegalArgumentException when the root is missing or not at level zero
	 */
	public static void validateRoot(NodePattern root) {
		Integer level = root == null ? null : root.level();
		if (level == null || level != ROOT_LEVEL) {
			throw new IllegalArgumentException("The thread tree root must have level 0.");
		}
	}

	@Override
	public void validate(NodePattern node) {
		if (node == null) {
			throw new IllegalArgumentException("Node must not be null.");
		}
		Integer level = node.level();
		if (level == null || level < FIRST_NODE_LEVEL) {
			throw new IllegalArgumentException("Every non-root node must have a positive level.");
		}

		if (node instanceof EDTNode && (!node.isEDT() || level != EDT_NODE_LEVEL)) {
			throw new IllegalArgumentException("EDTNode must run on the EDT and have level " + EDT_NODE_LEVEL + ".");
		}
		if (node instanceof RunnableNode && node.isEDT()) {
			throw new IllegalArgumentException("RunnableNode must not run on the EDT.");
		}
	}

}
