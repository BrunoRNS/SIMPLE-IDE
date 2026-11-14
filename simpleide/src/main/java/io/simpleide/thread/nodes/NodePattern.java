package io.simpleide.thread.nodes;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public interface NodePattern {

  public abstract Runnable onStart();

  public abstract Runnable onClose();

  public abstract Integer level();

  public abstract boolean isEDT();

  public default BigInteger getId() {
    return NodeIds.idFor(this);
  }

  final class NodeIds {
    private static final ReferenceQueue<NodePattern> COLLECTED = new ReferenceQueue<>();
    private static final Map<IdentityReference, BigInteger> IDS = new HashMap<>();
    private static final NodeIdGenerator NEXT = new NodeIdGenerator();

    private NodeIds() {}

    private static BigInteger idFor(NodePattern node) {
      synchronized (IDS) {
        IdentityReference collected;
        while ((collected = (IdentityReference) COLLECTED.poll()) != null) {
          IDS.remove(collected);
        }

        IdentityReference lookup = new IdentityReference(node, null);
        BigInteger existing = IDS.get(lookup);
        if (existing != null) {
          return existing;
        }

        BigInteger id = NEXT.nextId();
        IDS.put(new IdentityReference(node, COLLECTED), id);
        return id;
      }
    }
  }

  final class IdentityReference extends WeakReference<NodePattern> {
    private final int hash;

    private IdentityReference(NodePattern node, ReferenceQueue<NodePattern> queue) {
      super(node, queue);
      hash = System.identityHashCode(node);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof IdentityReference reference)) {
        return false;
      }
      NodePattern node = get();
      return node != null && node == reference.get();
    }
  }
}
