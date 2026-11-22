package io.simpleide.thread.nodes.edt.listeners;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Property change listener that routes callbacks through the thread manager.
 *
 * <p>
 * Component property changes are dispatched as EDT nodes, so theme and font updates never
 * run directly inside the originating setter.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class PropertyChangeNode extends EDTNode implements PropertyChangeListener {

	private final Consumer<PropertyChangeEvent> handler;

	/**
	 * Creates a property change node.
	 * @param handler callback invoked on the EDT, must not be {@code null}
	 */
	public PropertyChangeNode(Consumer<PropertyChangeEvent> handler) {
		this.handler = Objects.requireNonNull(handler, "handler");
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		ThreadMaster.getInstance().executeOnEDT(() -> handler.accept(event));
	}

}
