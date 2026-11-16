package io.simpleide.thread.nodes.record;

import java.math.BigInteger;

/**
 * Immutable snapshot of a thread tree node.
 *
 * @param onStart action executed when the node starts
 * @param onClose action executed when the node finishes or is cancelled
 * @param level depth of the node in the thread tree
 * @param id stable identifier of the node
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public record NodeRecord(Runnable onStart, Runnable onClose, Integer level, BigInteger id) {
}
