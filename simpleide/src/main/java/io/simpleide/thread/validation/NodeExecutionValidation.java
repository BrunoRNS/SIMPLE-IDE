package io.simpleide.thread.validation;

import java.awt.EventQueue;

import io.simpleide.thread.nodes.NodePattern;

/**
 * Verifies that a node executes on the thread kind it declares.
 *
 * <p>
 * EDT nodes must run on the Swing Event Dispatch Thread, and background nodes must not.
 * The check protects the thread tree from silently running Swing work on a worker.
 *
 * <p>
 * Covered by {@code NodeExecutionValidationTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class NodeExecutionValidation implements Validation<NodePattern> {

	public static final NodeExecutionValidation INSTANCE = new NodeExecutionValidation();

	private NodeExecutionValidation() {
	}

	@Override
	public void validate(NodePattern node) {
		boolean runningOnEdt = EventQueue.isDispatchThread();
		if (node.isEDT() != runningOnEdt) {
			throw new IllegalStateException("Thread node execution context does not match its EDT configuration.");
		}
	}

}
