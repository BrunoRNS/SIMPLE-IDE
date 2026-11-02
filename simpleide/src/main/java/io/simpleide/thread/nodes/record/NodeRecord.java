package io.simpleide.thread.nodes.record;

public record NodeRecord(Runnable onStart, Runnable onClose, Integer level) {}
