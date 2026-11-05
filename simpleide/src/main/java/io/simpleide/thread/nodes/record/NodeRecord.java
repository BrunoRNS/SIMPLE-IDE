package io.simpleide.thread.nodes.record;

import java.math.BigInteger;

public record NodeRecord(Runnable onStart, Runnable onClose, Integer level, BigInteger id) {}
