package io.simpleide.settings;

import io.simpleide.thread.nodes.edt.listeners.SwingActionNode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Installs customizable shortcuts as window-level Swing key bindings.
 *
 * <p>
 * The manager parses the shortcut map from {@link IdeSettings}, keeps the resulting
 * keystrokes and installs one {@link SwingActionNode} per action on a root component. All
 * commands therefore run through the thread manager. Reinstalling removes the previous
 * bindings first, so settings changes take effect without restarting the application.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class KeybindingManager {

	private static final String ACTION_KEY_PREFIX = "simpleide.global.";

	private final Map<IdeAction, KeyStroke> keybindings = new EnumMap<>(IdeAction.class);

	private final Map<String, KeyStroke> installedBindings = new LinkedHashMap<>();

	private JComponent installedComponent;

	/**
	 * Creates a manager from the configured shortcuts.
	 * @param settings settings containing the shortcut map
	 */
	public KeybindingManager(IdeSettings settings) {
		reload(settings);
	}

	/**
	 * Re-reads every shortcut from the settings.
	 * @param settings settings containing the shortcut map
	 */
	public void reload(IdeSettings settings) {
		Objects.requireNonNull(settings, "settings");
		keybindings.clear();
		for (IdeAction action : IdeAction.values()) {
			KeyStroke keyStroke = IdeAction.parseKeyStroke(settings.keybindingOf(action));
			if (keyStroke == null) {
				keyStroke = action.defaultKeyStroke();
			}
			if (keyStroke != null) {
				keybindings.put(action, keyStroke);
			}
		}
	}

	/**
	 * Returns the keystroke configured for an action.
	 * @param action action to query
	 * @return the configured keystroke, or {@code null} when the action has no shortcut
	 */
	public KeyStroke getKeyStroke(IdeAction action) {
		return keybindings.get(action);
	}

	/**
	 * Installs the shortcuts on a root component.
	 * @param component component receiving the window-level bindings
	 * @param actions command executed for each action
	 */
	public void install(JComponent component, Map<IdeAction, Runnable> actions) {
		Objects.requireNonNull(component, "component");
		Objects.requireNonNull(actions, "actions");
		uninstall();
		installedComponent = component;
		for (IdeAction action : IdeAction.values()) {
			KeyStroke keyStroke = keybindings.get(action);
			Runnable command = actions.get(action);
			if (keyStroke == null || command == null) {
				continue;
			}
			String actionKey = actionKeyFor(action);
			component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionKey);
			component.getActionMap().put(actionKey, new SwingActionNode(action.getLabel(), command));
			installedBindings.put(actionKey, keyStroke);
		}
	}

	/**
	 * Removes every binding installed by this manager.
	 */
	public void uninstall() {
		if (installedComponent == null) {
			return;
		}
		InputMap inputMap = installedComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = installedComponent.getActionMap();
		for (Map.Entry<String, KeyStroke> binding : installedBindings.entrySet()) {
			if (binding.getKey().equals(inputMap.get(binding.getValue()))) {
				inputMap.remove(binding.getValue());
			}
			actionMap.remove(binding.getKey());
		}
		installedBindings.clear();
		installedComponent = null;
	}

	/**
	 * Applies a shortcut to a settings instance.
	 * @param settings settings to update
	 * @param action action to configure
	 * @param keybinding shortcut text, or an empty string to restore the default
	 * @return {@code true} when the text is a valid shortcut
	 */
	public static boolean applyKeybinding(IdeSettings settings, IdeAction action, String keybinding) {
		if (keybinding == null || keybinding.isBlank()) {
			settings.setKeybinding(action, "");
			return true;
		}
		if (IdeAction.parseKeyStroke(keybinding) == null) {
			return false;
		}
		settings.setKeybinding(action, keybinding);
		return true;
	}

	private static String actionKeyFor(IdeAction action) {
		return ACTION_KEY_PREFIX + action.getId();
	}

}
