package io.simpleide.settings;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;

import javax.swing.KeyStroke;

/**
 * Application commands whose keyboard shortcuts can be customized.
 *
 * <p>
 * Each action has a stable identifier used in the settings file, a human readable label
 * shown in the settings view and a default shortcut. The shortcut strings use the syntax
 * accepted by {@link KeyStroke#getKeyStroke(String)}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public enum IdeAction {

	/**
	 * Creates a file in the open folder, or asks for a location when no folder is open.
	 */
	NEW_FILE("newFile", "New File", "ctrl N"),

	/**
	 * Selects a folder and shows it in the Explorer.
	 */
	OPEN_FOLDER("openFolder", "Open Folder", "ctrl shift O"),

	/**
	 * Clears the Explorer and closes every editor.
	 */
	CLOSE_FOLDER("closeFolder", "Close Folder", "ctrl shift K"),

	/**
	 * Closes the editor selected in the tabbed pane.
	 */
	CLOSE_EDITOR("closeEditor", "Close Current Editor", "ctrl W"),

	/**
	 * Closes every open editor.
	 */
	CLOSE_ALL_EDITORS("closeAllEditors", "Close All Editors", "ctrl shift W"),

	/**
	 * Saves every modified editor.
	 */
	SAVE_ALL("saveAll", "Save All Editors", "ctrl shift S"),

	/**
	 * Shows or hides the left sidebar.
	 */
	TOGGLE_SIDEBAR("toggleSidebar", "Toggle Sidebar", "ctrl B"),

	/**
	 * Opens the workspace search view.
	 */
	WORKSPACE_SEARCH("workspaceSearch", "Search Workspace", "ctrl shift F"),

	/**
	 * Opens the settings view.
	 */
	OPEN_SETTINGS("openSettings", "Open Settings", "ctrl alt S");

	private final String id;

	private final String label;

	private final String defaultKeybinding;

	IdeAction(String id, String label, String defaultKeybinding) {
		this.id = id;
		this.label = label;
		this.defaultKeybinding = defaultKeybinding;
	}

	/**
	 * Returns the stable settings identifier.
	 * @return the identifier stored in the settings file
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the label shown in the settings view.
	 * @return the human readable action name
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Returns the shortcut used when the settings file does not define one.
	 * @return the default shortcut text
	 */
	public String getDefaultKeybinding() {
		return defaultKeybinding;
	}

	/**
	 * Returns the default shortcut parsed into a Swing keystroke.
	 * @return the default keystroke, or {@code null} when the default cannot be parsed
	 */
	public KeyStroke defaultKeyStroke() {
		return KeyStroke.getKeyStroke(defaultKeybinding);
	}

	/**
	 * Finds an action by its stable identifier.
	 * @param id settings identifier
	 * @return the matching action, or an empty optional when the identifier is unknown
	 */
	public static Optional<IdeAction> fromId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		for (IdeAction action : values()) {
			if (action.id.equals(id)) {
				return Optional.of(action);
			}
		}
		return Optional.empty();
	}

	/**
	 * Parses a shortcut string.
	 * @param keybinding text such as {@code ctrl shift S}
	 * @return the parsed keystroke, or {@code null} when the text is empty or invalid
	 */
	public static KeyStroke parseKeyStroke(String keybinding) {
		if (keybinding == null || keybinding.isBlank()) {
			return null;
		}
		try {
			return KeyStroke.getKeyStroke(keybinding.trim());
		}
		catch (RuntimeException error) {
			return null;
		}
	}

	/**
	 * Formats a keystroke for display and persistence.
	 * @param keyStroke keystroke to format
	 * @return a shortcut string, or an empty string when the keystroke is {@code null}
	 */
	public static String formatKeyStroke(KeyStroke keyStroke) {
		if (keyStroke == null) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		int modifiers = keyStroke.getModifiers();
		if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
			text.append("ctrl ");
		}
		if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
			text.append("alt ");
		}
		if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
			text.append("shift ");
		}
		if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
			text.append("meta ");
		}
		int keyCode = keyStroke.getKeyCode();
		String keyText = KeyEvent.getKeyText(keyCode);
		if (keyText == null || keyText.isBlank()) {
			return "";
		}
		return text.append(keyText.toUpperCase(java.util.Locale.ROOT)).toString();
	}

}
