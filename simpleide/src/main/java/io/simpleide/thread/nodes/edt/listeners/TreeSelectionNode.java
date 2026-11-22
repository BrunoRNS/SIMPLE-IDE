package io.simpleide.thread.nodes.edt.listeners;

import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

/**
 * Tree selection listener that routes callbacks through the thread manager.
 *
 * <p>
 * The selected path change is dispatched as an EDT node, so selection work never runs
 * directly inside the Swing callback.
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
		ThreadMaster.getInstance().executeOnEDT(() -> handler.accept(event));
	}

}
