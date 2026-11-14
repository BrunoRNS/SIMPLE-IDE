package io.simpleide.thread.nodes;

import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

final class NodeIdGenerator {

  private final AtomicReference<BigInteger> currentId;

  NodeIdGenerator() {
    this(BigInteger.ZERO);
  }

  NodeIdGenerator(BigInteger initialId) {
    Objects.requireNonNull(initialId, "initialId");
    if (initialId.signum() < 0) {
      throw new IllegalArgumentException("The initial node ID must not be negative.");
    }
    currentId = new AtomicReference<>(initialId);
  }

  BigInteger nextId() {
    return currentId.updateAndGet(current -> current.add(BigInteger.ONE));
  }
}
