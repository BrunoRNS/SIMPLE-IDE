package io.simpleide.thread.tree;

import io.simpleide.thread.nodes.NodePattern;
import java.math.BigInteger;
import java.util.Map;

public interface Tree {

  public void addNode(NodePattern node);

  public void removeNodeCascade(NodePattern node);

  public Map<BigInteger, TreeNode> showTree();
}
