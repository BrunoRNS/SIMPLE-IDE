package io.simpleide.settings;

import java.util.LinkedHashMap;
import java.util.Map;

import io.simpleide.ui.menubar.ThemeManager;

/**
 * Persistent application settings.
 *
 * <p>
 * The bean is serialized to {@code ~/.simpleide/settings.json}. It stores the selected
 * theme, the interface and editor fonts, editor behavior options and the customized
 * shortcut of every {@link IdeAction}. Unknown values are replaced with defaults when the
 * bean is normalized.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class IdeSettings {

	/**
	 * Default interface font family.
	 */
	public static final String DEFAULT_UI_FONT_FAMILY = "SansSerif";

	/**
	 * Default interface font size.
	 */
	public static final int DEFAULT_UI_FONT_SIZE = 12;

	/**
	 * Default editor font family.
	 */
	public static final String DEFAULT_EDITOR_FONT_FAMILY = "Monospaced";

	/**
	 * Default editor font size.
	 */
	public static final int DEFAULT_EDITOR_FONT_SIZE = 13;

	/**
	 * Default number of spaces inserted for one tab.
	 */
	public static final int DEFAULT_TAB_SIZE = 4;

	private String theme = ThemeManager.ThemeType.DARK.name();

	private String uiFontFamily = DEFAULT_UI_FONT_FAMILY;

	private int uiFontSize = DEFAULT_UI_FONT_SIZE;

	private String editorFontFamily = DEFAULT_EDITOR_FONT_FAMILY;

	private int editorFontSize = DEFAULT_EDITOR_FONT_SIZE;

	private int tabSize = DEFAULT_TAB_SIZE;

	private boolean wordWrap;

	private boolean showWhitespace;

	private boolean showLineNumbers = true;

	private Map<String, String> keybindings = defaultKeybindings();

	/**
	 * Creates settings populated with the application defaults.
	 */
	public IdeSettings() {
	}

	/**
	 * Creates a normalized copy of another settings instance.
	 * @param source settings to copy
	 */
	public IdeSettings(IdeSettings source) {
		this.theme = source.theme;
		this.uiFontFamily = source.uiFontFamily;
		this.uiFontSize = source.uiFontSize;
		this.editorFontFamily = source.editorFontFamily;
		this.editorFontSize = source.editorFontSize;
		this.tabSize = source.tabSize;
		this.wordWrap = source.wordWrap;
		this.showWhitespace = source.showWhitespace;
		this.showLineNumbers = source.showLineNumbers;
		this.keybindings = new LinkedHashMap<>(source.keybindings);
		normalize();
	}

	/**
	 * Returns the selected theme name.
	 * @return a {@link ThemeManager.ThemeType} name
	 */
	public String getTheme() {
		return theme;
	}

	/**
	 * Selects the application theme.
	 * @param theme a {@link ThemeManager.ThemeType} name
	 */
	public void setTheme(String theme) {
		this.theme = theme;
	}

	/**
	 * Returns the interface font family.
	 * @return the font family used by Swing components
	 */
	public String getUiFontFamily() {
		return uiFontFamily;
	}

	/**
	 * Selects the interface font family.
	 * @param uiFontFamily font family name
	 */
	public void setUiFontFamily(String uiFontFamily) {
		this.uiFontFamily = uiFontFamily;
	}

	/**
	 * Returns the interface font size.
	 * @return the size in points
	 */
	public int getUiFontSize() {
		return uiFontSize;
	}

	/**
	 * Selects the interface font size.
	 * @param uiFontSize size in points
	 */
	public void setUiFontSize(int uiFontSize) {
		this.uiFontSize = uiFontSize;
	}

	/**
	 * Returns the editor font family.
	 * @return the editor font family name
	 */
	public String getEditorFontFamily() {
		return editorFontFamily;
	}

	/**
	 * Selects the editor font family.
	 * @param editorFontFamily font family name
	 */
	public void setEditorFontFamily(String editorFontFamily) {
		this.editorFontFamily = editorFontFamily;
	}

	/**
	 * Returns the editor font size.
	 * @return the size in points
	 */
	public int getEditorFontSize() {
		return editorFontSize;
	}

	/**
	 * Selects the editor font size.
	 * @param editorFontSize size in points
	 */
	public void setEditorFontSize(int editorFontSize) {
		this.editorFontSize = editorFontSize;
	}

	/**
	 * Returns the tab size.
	 * @return the number of spaces used for a tab
	 */
	public int getTabSize() {
		return tabSize;
	}

	/**
	 * Selects the tab size.
	 * @param tabSize number of spaces
	 */
	public void setTabSize(int tabSize) {
		this.tabSize = tabSize;
	}

	/**
	 * Returns whether editor lines wrap.
	 * @return {@code true} when word wrap is enabled
	 */
	public boolean isWordWrap() {
		return wordWrap;
	}

	/**
	 * Enables or disables editor word wrap.
	 * @param wordWrap new value
	 */
	public void setWordWrap(boolean wordWrap) {
		this.wordWrap = wordWrap;
	}

	/**
	 * Returns whether whitespace characters are visible.
	 * @return {@code true} when whitespace is painted
	 */
	public boolean isShowWhitespace() {
		return showWhitespace;
	}

	/**
	 * Enables or disables whitespace painting.
	 * @param showWhitespace new value
	 */
	public void setShowWhitespace(boolean showWhitespace) {
		this.showWhitespace = showWhitespace;
	}

	/**
	 * Returns whether editor line numbers are shown.
	 * @return {@code true} when the line number gutter is enabled
	 */
	public boolean isShowLineNumbers() {
		return showLineNumbers;
	}

	/**
	 * Enables or disables editor line numbers.
	 * @param showLineNumbers new value
	 */
	public void setShowLineNumbers(boolean showLineNumbers) {
		this.showLineNumbers = showLineNumbers;
	}

	/**
	 * Returns the customized shortcuts keyed by {@link IdeAction#getId() action id}.
	 * @return the mutable shortcut map
	 */
	public Map<String, String> getKeybindings() {
		return keybindings;
	}

	/**
	 * Replaces the shortcut map.
	 * @param keybindings shortcuts keyed by action id
	 */
	public void setKeybindings(Map<String, String> keybindings) {
		this.keybindings = keybindings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keybindings);
	}

	/**
	 * Returns the configured shortcut text for an action.
	 * @param action action to query
	 * @return the configured shortcut, falling back to the action default
	 */
	public String keybindingOf(IdeAction action) {
		String keybinding = keybindings.get(action.getId());
		return keybinding == null || keybinding.isBlank() ? action.getDefaultKeybinding() : keybinding;
	}

	/**
	 * Stores a shortcut for an action.
	 * @param action action to configure
	 * @param keybinding shortcut text, or an empty string to use the default
	 */
	public void setKeybinding(IdeAction action, String keybinding) {
		if (keybinding == null || keybinding.isBlank()) {
			keybindings.remove(action.getId());
		}
		else {
			keybindings.put(action.getId(), keybinding.trim());
		}
	}

	/**
	 * Restores every value to the application default.
	 */
	public void resetToDefaults() {
		theme = ThemeManager.ThemeType.DARK.name();
		uiFontFamily = DEFAULT_UI_FONT_FAMILY;
		uiFontSize = DEFAULT_UI_FONT_SIZE;
		editorFontFamily = DEFAULT_EDITOR_FONT_FAMILY;
		editorFontSize = DEFAULT_EDITOR_FONT_SIZE;
		tabSize = DEFAULT_TAB_SIZE;
		wordWrap = false;
		showWhitespace = false;
		showLineNumbers = true;
		keybindings = defaultKeybindings();
	}

	/**
	 * Replaces missing or invalid values with defaults.
	 */
	public void normalize() {
		if (parseTheme(theme) == null) {
			theme = ThemeManager.ThemeType.DARK.name();
		}
		if (uiFontFamily == null || uiFontFamily.isBlank()) {
			uiFontFamily = DEFAULT_UI_FONT_FAMILY;
		}
		if (editorFontFamily == null || editorFontFamily.isBlank()) {
			editorFontFamily = DEFAULT_EDITOR_FONT_FAMILY;
		}
		uiFontSize = clamp(uiFontSize, 8, 48, DEFAULT_UI_FONT_SIZE);
		editorFontSize = clamp(editorFontSize, 8, 72, DEFAULT_EDITOR_FONT_SIZE);
		tabSize = clamp(tabSize, 1, 16, DEFAULT_TAB_SIZE);
		if (keybindings == null) {
			keybindings = new LinkedHashMap<>();
		}
		Map<String, String> normalized = new LinkedHashMap<>();
		for (IdeAction action : IdeAction.values()) {
			String keybinding = keybindings.get(action.getId());
			if (keybinding == null || keybinding.isBlank()) {
				keybinding = action.getDefaultKeybinding();
			}
			if (IdeAction.parseKeyStroke(keybinding) != null) {
				normalized.put(action.getId(), keybinding.trim());
			}
			else {
				normalized.put(action.getId(), action.getDefaultKeybinding());
			}
		}
		keybindings = normalized;
	}

	/**
	 * Parses a theme name.
	 * @param name theme name
	 * @return the matching theme, or {@code null} when the name is invalid
	 */
	public static ThemeManager.ThemeType parseTheme(String name) {
		if (name == null) {
			return null;
		}
		try {
			return ThemeManager.ThemeType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException error) {
			return null;
		}
	}

	private static int clamp(int value, int minimum, int maximum, int fallback) {
		if (value < minimum || value > maximum) {
			return fallback;
		}
		return value;
	}

	private static Map<String, String> defaultKeybindings() {
		Map<String, String> defaults = new LinkedHashMap<>();
		for (IdeAction action : IdeAction.values()) {
			defaults.put(action.getId(), action.getDefaultKeybinding());
		}
		return defaults;
	}

}
