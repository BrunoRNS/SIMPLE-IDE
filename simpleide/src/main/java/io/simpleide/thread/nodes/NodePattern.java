package io.simpleide.thread.nodes;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Contract for a unit of work tracked by the thread tree.
 *
 * <p>
 * Implementations provide the action to run on start, an optional close action, the node
 * level and whether the work must execute on the Swing Event Dispatch Thread. The default
 * identifier is stable for the lifetime of the pattern and unique within the running JVM.
 *
 * <p>
 * Covered by {@code NodePatternTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public interface NodePattern {

	/**
	 * Returns the action executed when the node starts.
	 * @return the start action, or {@code null} for no work
	 */
	public abstract Runnable onStart();

	/**
	 * Returns the action executed when the node finishes or is cancelled.
	 * @return the close action, or {@code null}
	 */
	public abstract Runnable onClose();

	/**
	 * Returns the depth of this node in the thread tree.
	 * @return the level, where the root is zero
	 */
	public abstract Integer level();

	/**
	 * Returns whether the node must execute on the Swing Event Dispatch Thread.
	 * @return {@code true} for EDT work, {@code false} for a background worker
	 */
	public abstract boolean isEDT();

	/**
	 * Returns the stable identifier assigned to this pattern.
	 * @return a unique, lazily assigned identifier
	 */
	public default BigInteger getId() {
		return NodeIds.idFor(this);
	}

	final class NodeIds {

		private static final ReferenceQueue<NodePattern> COLLECTED = new ReferenceQueue<>();

		private static final Map<IdentityReference, BigInteger> IDS = new HashMap<>();

		private static final NodeIdGenerator NEXT = new NodeIdGenerator();

		private NodeIds() {
		}

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
