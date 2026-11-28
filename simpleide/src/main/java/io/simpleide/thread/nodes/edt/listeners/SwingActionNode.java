package io.simpleide.thread.nodes.edt.listeners;

import java.awt.event.ActionEvent;
import java.util.Objects;

import javax.swing.AbstractAction;

import io.simpleide.thread.ThreadMaster;

/**
 * Swing {@link javax.swing.Action} that routes its command through the thread manager.
 *
 * <p>
 * Use this action for input-map and action-map bindings. The command is registered as an
 * EDT node instead of running directly inside {@code actionPerformed}, keeping keyboard
 * shortcuts under the same lifecycle as menu and button actions.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class SwingActionNode extends AbstractAction {

	private static final long serialVersionUID = 1L;

	private final Runnable command;

	/**
	 * Creates an action node.
	 * @param name action name
	 * @param command command invoked on the EDT, must not be {@code null}
	 */
	public SwingActionNode(String name, Runnable command) {
		super(name);
		this.command = Objects.requireNonNull(command, "command");
	}

	@Override
	public void actionPerformed(ActionEvent event) {
		ThreadMaster.getInstance().executeOnEDT(command);
	}

}
