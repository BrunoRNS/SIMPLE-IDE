package io.simpleide.ui.menubar.menus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import io.simpleide.settings.IdeAction;
import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.SettingsService;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;

/**
 * Execute menu with the Run, Debug and Stop commands.
 *
 * <p>
 * Run is bound to F5 and Debug to Ctrl+F5 by default. Both shortcuts come from the
 * persistent settings and can be refreshed with {@link #applySettings(IdeSettings)}. When
 * no debug adapter is available for the current platform,
 * {@link #setDebugEnabled(boolean)} disables the Debug item and explains why in its
 * tooltip. The Stop item is always present so a running program or debug session can be
 * interrupted at any time from the menu bar.
 *
 * <p>
 * Covered by {@code ExecuteMenuTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public class ExecuteMenu extends JMenu {

	/**
	 * Tooltip shown while the Debug action is unavailable.
	 */
	public static final String DEBUG_UNAVAILABLE_TOOLTIP = "Debugging is not available: no compatible debug adapter could be installed.";

	/**
	 * Tooltip shown while there is nothing running to stop.
	 */
	public static final String STOP_IDLE_TOOLTIP = "There is no program or debug session running to stop.";

	private final JMenuItem runItem = new JMenuItem("Run");

	private final JMenuItem debugItem = new JMenuItem("Debug");

	private final JMenuItem stopItem = new JMenuItem("Stop");

	/**
	 * Creates the Execute menu with its default shortcuts.
	 * @param run action that runs the selected program
	 * @param debug action that starts a debug session
	 * @throws NullPointerException when an action is {@code null}
	 */
	public ExecuteMenu(Runnable run, Runnable debug) {
		super("Execute");
		Objects.requireNonNull(run, "run");
		Objects.requireNonNull(debug, "debug");
		runItem.addActionListener(new ActionNode(event -> run.run()));
		debugItem.addActionListener(new ActionNode(event -> debug.run()));
		stopItem.addActionListener(new ActionNode(event -> {
			Runnable stop = stopAction.get();
			if (stop != null) {
				stop.run();
			}
		}));
		add(runItem);
		add(debugItem);
		add(stopItem);
		applySettings(SettingsService.getInstance().getSettings());
		setStopEnabled(true);
	}

	private final AtomicReference<Runnable> stopAction = new AtomicReference<>(null);

	/**
	 * Wires the action invoked by the Stop menu item.
	 * @param stop action that stops the running program or debug session
	 */
	public void setStopAction(Runnable stop) {
		stopAction.set(stop == null ? null : stop);
	}

	/**
	 * Enables or disables the Stop command.
	 * @param enabled {@code true} when a program or debug session is running
	 */
	public void setStopEnabled(boolean enabled) {
		stopItem.setEnabled(enabled);
		stopItem.setToolTipText(enabled ? "Stop the running program or debug session" : STOP_IDLE_TOOLTIP);
	}

	/**
	 * Refreshes the shortcut labels from the settings.
	 * @param settings settings containing the configured shortcuts
	 */
	public void applySettings(IdeSettings settings) {
		Objects.requireNonNull(settings, "settings");
		runItem.setAccelerator(IdeAction.parseKeyStroke(settings.keybindingOf(IdeAction.RUN)));
		debugItem.setAccelerator(IdeAction.parseKeyStroke(settings.keybindingOf(IdeAction.DEBUG)));
	}

	/**
	 * Enables or disables the Debug command.
	 * @param enabled {@code false} when no debug adapter is available
	 */
	public void setDebugEnabled(boolean enabled) {
		debugItem.setEnabled(enabled);
		debugItem.setToolTipText(enabled ? "Start a debug session (Ctrl+F5)" : DEBUG_UNAVAILABLE_TOOLTIP);
	}

	/**
	 * Returns the Run menu item.
	 * @return the Run item
	 */
	public JMenuItem getRunItem() {
		return runItem;
	}

	/**
	 * Returns the Debug menu item.
	 * @return the Debug item
	 */
	public JMenuItem getDebugItem() {
		return debugItem;
	}

	/**
	 * Returns the Stop menu item.
	 * @return the Stop item
	 */
	public JMenuItem getStopItem() {
		return stopItem;
	}

}
