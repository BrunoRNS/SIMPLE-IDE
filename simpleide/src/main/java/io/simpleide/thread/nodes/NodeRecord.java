package io.simpleide.thread.nodes;

public record NodeRecord(Runnable onStart, Runnable onClose, Integer level) {}
