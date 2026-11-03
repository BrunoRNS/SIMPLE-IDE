package io.simpleide.thread.validation;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.edt.EDTNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

public final class NodeRegistrationValidation implements Validation<NodePattern> {

    public static final int ROOT_LEVEL = 0;
    public static final int FIRST_NODE_LEVEL = 1;
    public static final int EDT_NODE_LEVEL = FIRST_NODE_LEVEL;

    public static final NodeRegistrationValidation INSTANCE = new NodeRegistrationValidation();

    private NodeRegistrationValidation() {}

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
            throw new IllegalArgumentException(
                "EDTNode must run on the EDT and have level " + EDT_NODE_LEVEL + "."
            );
        }
        if (node instanceof RunnableNode && node.isEDT()) {
            throw new IllegalArgumentException("RunnableNode must not run on the EDT.");
        }
    }
}
