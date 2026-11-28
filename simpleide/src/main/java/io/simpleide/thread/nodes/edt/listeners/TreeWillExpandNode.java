package io.simpleide.thread.nodes.edt.listeners;

import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.ExpandVetoException;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

/**
 * Tree expansion listener that routes callbacks through the thread manager.
 *
 * <p>
 * Expansion and collapse notifications are dispatched as EDT nodes. Vetoing an expansion
 * is not supported because the callback runs after the listener returns; consumers should
 * adjust the tree model instead.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class TreeWillExpandNode extends EDTNode implements TreeWillExpandListener {

	private Consumer<TreeExpansionEvent> expandHandler;

	private Consumer<TreeExpansionEvent> collapseHandler;

	/**
	 * Registers an expansion handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public TreeWillExpandNode onExpand(Consumer<TreeExpansionEvent> handler) {
		this.expandHandler = handler;
		return this;
	}

	/**
	 * Registers a collapse handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public TreeWillExpandNode onCollapse(Consumer<TreeExpansionEvent> handler) {
		this.collapseHandler = handler;
		return this;
	}

	@Override
	public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
		dispatch(expandHandler, event);
	}

	@Override
	public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
		dispatch(collapseHandler, event);
	}

	private void dispatch(Consumer<TreeExpansionEvent> handler, TreeExpansionEvent event) {
		if (handler == null) {
			return;
		}
		if (SwingUtilities.isEventDispatchThread()) {
			handler.accept(event);
		}
		else {
			ThreadMaster.getInstance().executeOnEDT(() -> handler.accept(event));
		}
	}

}
