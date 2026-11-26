package io.simpleide.ui.menubar.menus;

import java.util.Objects;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import io.simpleide.settings.IdeAction;
import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.SettingsService;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;

/**
 * Execute menu with the Run and Debug commands.
 *
 * <p>
 * Run is bound to F5 and Debug to Ctrl+F5 by default. Both shortcuts come from the
 * persistent settings and can be refreshed with {@link #applySettings(IdeSettings)}. When
 * no debug adapter is available for the current platform,
 * {@link #setDebugEnabled(boolean)} disables the Debug item and explains why in its
 * tooltip.
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

	private final JMenuItem runItem = new JMenuItem("Run");

	private final JMenuItem debugItem = new JMenuItem("Debug");

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
		add(runItem);
		add(debugItem);
		applySettings(SettingsService.getInstance().getSettings());
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

}
