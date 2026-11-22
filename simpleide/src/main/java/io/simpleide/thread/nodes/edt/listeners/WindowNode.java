package io.simpleide.thread.nodes.edt.listeners;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.function.Consumer;

/**
 * Window listener that routes callbacks through the thread manager.
 *
 * <p>
 * Every window event is dispatched through {@link ThreadMaster#executeOnEDT(Runnable)},
 * so closing, opening and activation work is tracked as an EDT node. Handlers are
 * optional and configured with the fluent {@code on*} methods.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class WindowNode extends EDTNode implements WindowListener {

	private Consumer<WindowEvent> openedHandler;

	private Consumer<WindowEvent> closingHandler;

	private Consumer<WindowEvent> closedHandler;

	private Consumer<WindowEvent> iconifiedHandler;

	private Consumer<WindowEvent> deiconifiedHandler;

	private Consumer<WindowEvent> activatedHandler;

	private Consumer<WindowEvent> deactivatedHandler;

	/**
	 * Registers an opened handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public WindowNode onOpened(Consumer<WindowEvent> handler) {
		this.openedHandler = handler;
		return this;
	}

	/**
	 * Registers a closing handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public WindowNode onClosing(Consumer<WindowEvent> handler) {
		this.closingHandler = handler;
		return this;
	}

	/**
	 * Registers a closed handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public WindowNode onClosed(Consumer<WindowEvent> handler) {
		this.closedHandler = handler;
		return this;
	}

	/**
	 * Registers an iconified handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public WindowNode onIconified(Consumer<WindowEvent> handler) {
		this.iconifiedHandler = handler;
		return this;
	}

	/**
	 * Registers a deiconified handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public WindowNode onDeiconified(Consumer<WindowEvent> handler) {
		this.deiconifiedHandler = handler;
		return this;
	}

	/**
	 * Registers an activated handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public WindowNode onActivated(Consumer<WindowEvent> handler) {
		this.activatedHandler = handler;
		return this;
	}

	/**
	 * Registers a deactivated handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public WindowNode onDeactivated(Consumer<WindowEvent> handler) {
		this.deactivatedHandler = handler;
		return this;
	}

	@Override
	public void windowOpened(WindowEvent event) {
		dispatch(openedHandler, event);
	}

	@Override
	public void windowClosing(WindowEvent event) {
		dispatch(closingHandler, event);
	}

	@Override
	public void windowClosed(WindowEvent event) {
		dispatch(closedHandler, event);
	}

	@Override
	public void windowIconified(WindowEvent event) {
		dispatch(iconifiedHandler, event);
	}

	@Override
	public void windowDeiconified(WindowEvent event) {
		dispatch(deiconifiedHandler, event);
	}

	@Override
	public void windowActivated(WindowEvent event) {
		dispatch(activatedHandler, event);
	}

	@Override
	public void windowDeactivated(WindowEvent event) {
		dispatch(deactivatedHandler, event);
	}

	private void dispatch(Consumer<WindowEvent> handler, WindowEvent event) {
		if (handler == null) {
			return;
		}
		ThreadMaster.getInstance().executeOnEDT(() -> handler.accept(event));
	}

}
