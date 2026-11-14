package io.simpleide.thread.validation;

import io.simpleide.thread.nodes.NodePattern;
import java.awt.EventQueue;

public final class NodeExecutionValidation implements Validation<NodePattern> {

  public static final NodeExecutionValidation INSTANCE = new NodeExecutionValidation();

  private NodeExecutionValidation() {}

  @Override
  public void validate(NodePattern node) {
    boolean runningOnEdt = EventQueue.isDispatchThread();
    if (node.isEDT() != runningOnEdt) {
      throw new IllegalStateException(
          "Thread node execution context does not match its EDT configuration.");
    }
  }
}
