package io.simpleide.thread.nodes.edt.listeners;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.EDTNode;

/**
 * {@link javax.swing.event.DocumentListener} that coalesces changes into an EDT task.
 *
 * <p>
 * Insert, remove and attribute changes all schedule the same callback on the Swing Event
 * Dispatch Thread through the thread manager. Closing the node makes later changes a
 * no-op, which avoids scheduling work for an editor that is being discarded.
 *
 * <p>
 * Covered by {@code EDTNodeListenerTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class DocumentNode extends EDTNode implements DocumentListener, AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentNode.class);

	private final Runnable onChange;

	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Creates a node that invokes a callback after any document change.
	 * @param onChange callback scheduled on the EDT, must not be {@code null}
	 */
	public DocumentNode(Runnable onChange) {
		this.onChange = Objects.requireNonNull(onChange, "onChange");
	}

	@Override
	public void insertUpdate(DocumentEvent event) {
		dispatchChange();
	}

	@Override
	public void removeUpdate(DocumentEvent event) {
		dispatchChange();
	}

	@Override
	public void changedUpdate(DocumentEvent event) {
		dispatchChange();
	}

	private void dispatchChange() {
		if (closed.get()) {
			return;
		}
		try {
			ThreadMaster.getInstance().executeOnEDT(() -> {
				if (!closed.get()) {
					onChange.run();
				}
			});
		}
		catch (RuntimeException error) {
			LOGGER.error("Unable to schedule editor document change.", error);
		}
	}

	@Override
	public void close() {
		closed.set(true);
	}

}
