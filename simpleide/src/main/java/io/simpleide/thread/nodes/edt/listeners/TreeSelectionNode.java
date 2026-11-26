package io.simpleide.thread.nodes.edt.listeners;

import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

/**
 * Tree selection listener that dispatches callbacks through the thread manager.
 *
 * <p>
 * Swing delivers tree-selection events on the EDT. Each callback is queued as a managed
 * EDT node so listener work remains visible and cancellable in the thread tree.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class TreeSelectionNode extends EDTNode implements TreeSelectionListener {

	private final Consumer<TreeSelectionEvent> handler;

	/**
	 * Creates a selection node.
	 * @param handler callback invoked on the EDT, must not be {@code null}
	 */
	public TreeSelectionNode(Consumer<TreeSelectionEvent> handler) {
		this.handler = Objects.requireNonNull(handler, "handler");
	}

	@Override
	public void valueChanged(TreeSelectionEvent event) {
		if (SwingUtilities.isEventDispatchThread()) {
			handler.accept(event);
		}
		else {
			ThreadMaster.getInstance().executeOnEDT(() -> handler.accept(event));
		}
	}

}
