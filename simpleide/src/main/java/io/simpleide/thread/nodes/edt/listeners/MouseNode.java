package io.simpleide.thread.nodes.edt.listeners;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.function.Consumer;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

/**
 * Mouse listener that routes callbacks through the thread manager.
 *
 * <p>
 * Each received event is dispatched through {@link ThreadMaster#executeOnEDT(Runnable)},
 * so mouse handling is tracked as a cancellable EDT node instead of running directly
 * inside the Swing callback. Handlers are optional and can be configured with the fluent
 * {@code on*} methods.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class MouseNode extends EDTNode implements MouseListener, MouseMotionListener, MouseWheelListener {

	private Consumer<MouseEvent> clickHandler;

	private Consumer<MouseEvent> pressHandler;

	private Consumer<MouseEvent> releaseHandler;

	private Consumer<MouseEvent> enterHandler;

	private Consumer<MouseEvent> exitHandler;

	private Consumer<MouseEvent> moveHandler;

	private Consumer<MouseEvent> dragHandler;

	private Consumer<MouseWheelEvent> wheelHandler;

	/**
	 * Registers a click handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onClick(Consumer<MouseEvent> handler) {
		this.clickHandler = handler;
		return this;
	}

	/**
	 * Registers a press handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onPress(Consumer<MouseEvent> handler) {
		this.pressHandler = handler;
		return this;
	}

	/**
	 * Registers a release handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onRelease(Consumer<MouseEvent> handler) {
		this.releaseHandler = handler;
		return this;
	}

	/**
	 * Registers an enter handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onEnter(Consumer<MouseEvent> handler) {
		this.enterHandler = handler;
		return this;
	}

	/**
	 * Registers an exit handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onExit(Consumer<MouseEvent> handler) {
		this.exitHandler = handler;
		return this;
	}

	/**
	 * Registers a move handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onMove(Consumer<MouseEvent> handler) {
		this.moveHandler = handler;
		return this;
	}

	/**
	 * Registers a drag handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onDrag(Consumer<MouseEvent> handler) {
		this.dragHandler = handler;
		return this;
	}

	/**
	 * Registers a wheel handler.
	 * @param handler callback invoked on the EDT
	 * @return this node
	 */
	public MouseNode onWheel(Consumer<MouseWheelEvent> handler) {
		this.wheelHandler = handler;
		return this;
	}

	@Override
	public void mouseClicked(MouseEvent event) {
		dispatch(clickHandler, event);
	}

	@Override
	public void mousePressed(MouseEvent event) {
		dispatch(pressHandler, event);
	}

	@Override
	public void mouseReleased(MouseEvent event) {
		dispatch(releaseHandler, event);
	}

	@Override
	public void mouseEntered(MouseEvent event) {
		dispatch(enterHandler, event);
	}

	@Override
	public void mouseExited(MouseEvent event) {
		dispatch(exitHandler, event);
	}

	@Override
	public void mouseDragged(MouseEvent event) {
		dispatch(dragHandler, event);
	}

	@Override
	public void mouseMoved(MouseEvent event) {
		dispatch(moveHandler, event);
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent event) {
		if (wheelHandler == null) {
			return;
		}
		ThreadMaster.getInstance().executeOnEDT(() -> wheelHandler.accept(event));
	}

	private void dispatch(Consumer<MouseEvent> handler, MouseEvent event) {
		if (handler == null) {
			return;
		}
		ThreadMaster.getInstance().executeOnEDT(() -> handler.accept(event));
	}

}
