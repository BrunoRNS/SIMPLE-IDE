package io.simpleide.thread;

import io.simpleide.thread.nodes.NodePattern;

public final class ThreadMaster extends AbstractThreadMaster {

  private final NodePattern root;

  public ThreadMaster(NodePattern root) {
    super(root);
    this.root = root;
  }

  public NodePattern getROOT() {
    return root;
  }

  public static ThreadMaster getInstance() {
    return ThreadMasterHolder.INSTANCE;
  }
}
