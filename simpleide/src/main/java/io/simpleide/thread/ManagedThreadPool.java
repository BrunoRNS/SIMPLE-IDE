package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;

public final class ManagedThreadPool implements AutoCloseable {

  private final AbstractThreadMaster owner;
  private final NodePattern node;
  private final String name;

  ManagedThreadPool(AbstractThreadMaster owner, NodePattern node, String name) {
    this.owner = owner;
    this.node = node;
    this.name = name;
  }

  public NodePattern execute(Runnable action) {
    return execute(action, null);
  }

  public NodePattern execute(Runnable action, Runnable onClose) {
    return owner.executeOnPool(this, action, onClose);
  }

  @Override
  public void close() {
    owner.closePool(this);
  }

  AbstractThreadMaster owner() {
    return owner;
  }

  NodePattern node() {
    return node;
  }

  String name() {
    return name;
  }
}
