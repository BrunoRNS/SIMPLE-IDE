package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;

import io.simpleide.api.HexEditor;
import io.simpleide.frame.MainFrame;
import io.simpleide.frame.MainFrameHolder;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.SyntaxPalette;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorManager;
import io.simpleide.ui.sidebar.GitPanel;
import io.simpleide.ui.sidebar.SettingsPanel;

/**
 * Singleton that applies the application look and feel and editor colors.
 *
 * <p>
 * The manager keeps a set of registered editors so a theme change updates both the Swing
 * windows and the syntax highlighting. Themes include light, dark, Dracula, Dracula
 * Light, Brown and Brown Light. Theme changes are marshalled to the Swing Event Dispatch
 * Thread. Colors come from {@link ThemePalette}, which mirrors the official palettes of
 * VS Code Dark+/Light+, Dracula, Alucard and Gruvbox.
 *
 * <p>
 * Covered by {@code ThemeManagerTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/menubar.md">docs/api/ui/menubar.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public final class ThemeManager {

	private static final ThemeManager INSTANCE = new ThemeManager();

	private ThemeType currentTheme = ThemeType.DARK;

	private String uiFontFamily = "SansSerif";

	private int uiFontSize = 12;

	private String editorFontFamily = "Monospaced";

	private int editorFontSize = 13;

	private final List<Editor> editors = new ArrayList<>();

	private final List<HexEditor> hexEditors = new ArrayList<>();

	private ThemeManager() {
	}

	/**
	 * Returns the shared theme manager.
	 * @return the singleton instance
	 */
	public static ThemeManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Identifiers of the available application themes.
	 */
	public enum ThemeType {

		DARK, LIGHT, DRACULA, DRACULA_LIGHT, BROWN, BROWN_LIGHT,

	}

	/**
	 * Returns the palette for a theme.
	 * @param theme theme identifier
	 * @return the official palette for the theme
	 */
	public static ThemePalette paletteOf(ThemeType theme) {
		return ThemePalette.of(theme);
	}

	/**
	 * Returns the currently selected theme.
	 * @return the current theme identifier
	 */
	public ThemeType getCurrentTheme() {
		return currentTheme;
	}

	/**
	 * Returns the palette of the currently selected theme.
	 * @return the current palette
	 */
	public ThemePalette currentPalette() {
		return ThemePalette.of(currentTheme);
	}

	/**
	 * Applies the current theme to a window.
	 * @param window window to update, ignored when {@code null}
	 */
	public void register(Window window) {
		if (window == null) {
			return;
		}

		applyTheme(currentTheme);
	}

	/**
	 * Tracks an editor and applies the current editor theme to it.
	 * @param editor editor to track, ignored when {@code null}
	 */
	public void register(Editor editor) {
		if (editor == null) {
			return;
		}

		if (!editors.contains(editor)) {
			editors.add(editor);
		}
		ThreadMaster.getInstance().executeOnEDT(() -> applyEditorTheme(editor, currentTheme));
	}

	/**
	 * Stops tracking an editor that is being discarded.
	 * @param editor editor to remove
	 */
	public void unregister(Editor editor) {
		editors.remove(editor);
	}

	/** Tracks an HEX editor and applies the current palette to it. */
	public void register(HexEditor editor) {
		if (editor == null) {
			return;
		}
		if (!hexEditors.contains(editor)) {
			hexEditors.add(editor);
		}
		ThreadMaster.getInstance().executeOnEDT(() -> editor.applyTheme(currentPalette()));
	}

	/** Stops tracking an HEX editor that is being discarded. */
	public void unregister(HexEditor editor) {
		hexEditors.remove(editor);
	}

	/**
	 * Selects a theme and applies it to all windows and registered editors.
	 * @param newTheme theme to apply
	 */
	public void setTheme(ThemeType newTheme) {
		this.currentTheme = newTheme;
		applyTheme(newTheme);
	}

	/**
	 * Stores the interface and editor fonts used by the next theme application.
	 * @param uiFontFamily interface font family
	 * @param uiFontSize interface font size in points
	 * @param editorFontFamily editor font family
	 * @param editorFontSize editor font size in points
	 * @return {@code true} when at least one font value changed
	 */
	public boolean setFonts(String uiFontFamily, int uiFontSize, String editorFontFamily, int editorFontSize) {
		String normalizedUiFamily = normalizeFamily(uiFontFamily, "SansSerif");
		String normalizedEditorFamily = normalizeFamily(editorFontFamily, "Monospaced");
		int normalizedUiSize = Math.max(8, Math.min(48, uiFontSize));
		int normalizedEditorSize = Math.max(8, Math.min(72, editorFontSize));
		boolean changed = !Objects.equals(this.uiFontFamily, normalizedUiFamily) || this.uiFontSize != normalizedUiSize
				|| !Objects.equals(this.editorFontFamily, normalizedEditorFamily)
				|| this.editorFontSize != normalizedEditorSize;
		this.uiFontFamily = normalizedUiFamily;
		this.uiFontSize = normalizedUiSize;
		this.editorFontFamily = normalizedEditorFamily;
		this.editorFontSize = normalizedEditorSize;
		return changed;
	}

	/**
	 * Returns the interface font family.
	 * @return the configured interface font family
	 */
	public String getUiFontFamily() {
		return uiFontFamily;
	}

	/**
	 * Returns the interface font size.
	 * @return the configured interface font size in points
	 */
	public int getUiFontSize() {
		return uiFontSize;
	}

	/**
	 * Returns the editor font family.
	 * @return the configured editor font family
	 */
	public String getEditorFontFamily() {
		return editorFontFamily;
	}

	/**
	 * Returns the editor font size.
	 * @return the configured editor font size in points
	 */
	public int getEditorFontSize() {
		return editorFontSize;
	}

	/**
	 * Reapplies the configured fonts to every window and registered editor.
	 */
	public void refreshFonts() {
		ThreadMaster.getInstance().executeOnEDT(() -> {
			ThemeUIDefaults.applyFontDefaults(UIManager.getLookAndFeelDefaults(), uiFont(), editorFont());
			updateAllWindows();
			EditorManager.refreshFileIcons();
			for (Editor registeredEditor : editors) {
				if (registeredEditor != null) {
					applyEditorFont(registeredEditor);
				}
			}
		});
	}

	private static String normalizeFamily(String family, String fallback) {
		return family == null || family.isBlank() ? fallback : family.trim();
	}

	private Font uiFont() {
		return new Font(uiFontFamily, Font.PLAIN, uiFontSize);
	}

	private Font editorFont() {
		return new Font(editorFontFamily, Font.PLAIN, editorFontSize);
	}

	private void applyTheme(ThemeType theme) {
		ThreadMaster register = ThreadMaster.getInstance();
		register.executeOnEDT(() -> {
			switch (theme) {
				case LIGHT -> FlatLightLaf.setup();
				case DARK -> FlatDarkLaf.setup();
				case DRACULA -> setupPropertiesLookAndFeel("dracula.properties", "Dracula");
				case DRACULA_LIGHT -> setupPropertiesLookAndFeel("dracula-light.properties", "Dracula Light");
				case BROWN -> setupPropertiesLookAndFeel("brown.properties", "Brown");
				case BROWN_LIGHT -> setupPropertiesLookAndFeel("brown-light.properties", "Brown Light");
			}
			ThemeUIDefaults.apply(theme, uiFont(), editorFont());
			updateAllWindows();
			refreshOpenSidebarThemes();
			EditorManager.refreshFileIcons();
			for (Editor registeredEditor : editors) {
				if (registeredEditor != null) {
					applyEditorTheme(registeredEditor, theme);
				}
			}
			for (HexEditor hexEditor : hexEditors) {
				if (hexEditor != null) {
					hexEditor.applyTheme(ThemePalette.of(theme));
				}
			}
		});
	}

	private void refreshOpenSidebarThemes() {
		MainFrame mainFrame = MainFrameHolder.getMainFrameIfPresent();
		if (mainFrame == null || mainFrame.getSidebarPanel() == null) {
			return;
		}
		SettingsPanel settingsPanel = mainFrame.getSidebarPanel().getIfShownSettings();
		if (settingsPanel != null) {
			settingsPanel.updateThemeBox();
		}
		GitPanel gitPanel = mainFrame.getSidebarPanel().getIfShownGitPanel();
		if (gitPanel != null) {
			gitPanel.updateTheme();
		}
	}

	private void updateAllWindows() {
		for (Window window : Window.getWindows()) {
			if (window == null) {
				continue;
			}
			SwingUtilities.updateComponentTreeUI(window);
			updateMenuPopups(window);
			refreshComponentTheme(window, currentPalette());
			window.invalidate();
			window.validate();
			window.repaint();
		}
	}

	void refreshComponentTheme(Component component, ThemePalette palette) {
		if (component instanceof JMenuBar menuBar) {
			menuBar.setBackground(palette.menuBarBackground());
			menuBar.setForeground(palette.foreground());
			menuBar.setOpaque(true);
		}
		else if (component instanceof JPopupMenu popup) {
			popup.setBackground(palette.popupBackground());
			popup.setForeground(palette.popupForeground());
		}
		else if (component instanceof JMenuItem menuItem) {
			boolean topLevel = menuItem instanceof JMenu menu && menu.getParent() instanceof JMenuBar;
			menuItem.setForeground(topLevel ? palette.foreground() : palette.popupForeground());
			if (!topLevel) {
				menuItem.setBackground(palette.popupBackground());
				menuItem.setOpaque(true);
			}
			if (topLevel && menuItem instanceof JMenu menu) {
				menu.setBackground(palette.menuBarBackground());
				menu.setOpaque(true);
			}
		}
		else if (component instanceof AbstractButton button) {
			button.putClientProperty("JButton.buttonType", "roundRect");
			button.setBackground(palette.buttonBackground());
			button.setForeground(palette.buttonForeground());
		}
		else if (component instanceof JSpinner spinner) {
			spinner.setBackground(palette.inputBackground());
			spinner.setForeground(palette.inputForeground());
			spinner.setOpaque(true);
		}
		else if (component instanceof JSpinner.DefaultEditor editor) {
			editor.setBackground(palette.inputBackground());
			editor.setForeground(palette.inputForeground());
			editor.setOpaque(true);
		}
		else if (component instanceof JComboBox<?> comboBox) {
			comboBox.setBackground(palette.inputBackground());
			comboBox.setForeground(palette.inputForeground());
			comboBox.setOpaque(true);
		}
		else if (component instanceof JTextField textField) {
			textField.setBackground(palette.inputBackground());
			textField.setForeground(palette.inputForeground());
			textField.setCaretColor(palette.inputForeground());
			textField.setSelectionColor(palette.selectionBackground());
			textField.setSelectedTextColor(palette.selectionForeground());
			textField.setDisabledTextColor(palette.disabledForeground());
			textField.setOpaque(true);
		}
		else if (component instanceof JTabbedPane tabs) {
			tabs.setBackground(palette.surface());
			tabs.setForeground(palette.foreground());
		}
		else if (component instanceof JSplitPane splitPane) {
			splitPane.setBackground(palette.background());
			splitPane.setDividerSize(10);
		}
		else if (component instanceof JTree tree) {
			tree.setBackground(palette.background());
			tree.setForeground(palette.foreground());
		}
		else if (component instanceof JTextPane textPane) {
			textPane.setBackground(palette.background());
			textPane.setForeground(palette.foreground());
		}
		else if (component instanceof JViewport viewport) {
			viewport.setBackground(palette.background());
		}
		else if (component instanceof JScrollPane scrollPane) {
			scrollPane.setBackground(palette.background());
		}
		else if (component instanceof JPanel panel) {
			panel.setBackground(palette.background());
			panel.setForeground(palette.foreground());
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				refreshComponentTheme(child, palette);
			}
		}
		if (component instanceof JMenu menu) {
			refreshComponentTheme(menu.getPopupMenu(), palette);
		}
	}

	private void updateMenuPopups(Component component) {
		if (component == null) {
			return;
		}
		if (component instanceof JPopupMenu popup) {
			SwingUtilities.updateComponentTreeUI(popup);
			refreshComponentTheme(popup, currentPalette());
		}
		if (component instanceof JMenu menu) {
			SwingUtilities.updateComponentTreeUI(menu.getPopupMenu());
			updateMenuPopups(menu.getPopupMenu());
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				updateMenuPopups(child);
			}
		}
	}

	private void applyEditorTheme(Editor editor, ThemeType theme) {
		EditorThemeStyler.apply(editor, theme, this::applyEditorFont);
	}

	private void setupPropertiesLookAndFeel(String resourceName, String themeName) {
		ThemeLookAndFeel.setup(resourceName, themeName);
	}

	private void applyEditorFont(Editor editor) {
		Font font = editorFont();
		editor.setFont(font);
		Gutter gutter = RSyntaxUtilities.getGutter(editor);
		if (gutter != null) {
			gutter.setLineNumberFont(font);
		}
	}

	/**
	 * Applies the readable v1.0.0beta syntax colors for a theme.
	 *
	 * <p>
	 * Legacy implementation kept for code written against v1.0.0beta. New code must use
	 * {@link #applySyntaxPalette(Theme, SyntaxPalette)} with the palette returned by
	 * {@link ThemePalette#syntax()}.
	 * @param editorTheme editor theme to update
	 * @param theme active IDE theme
	 * @deprecated since v1.1.0, use {@link #applySyntaxPalette(Theme, SyntaxPalette)}
	 * instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void applyReadableSyntaxColors(Theme editorTheme, ThemeType theme) {
		LegacySyntaxColors.applyReadableSyntaxColors(editorTheme, theme);
	}

	/**
	 * Applies the v1.0.0beta theme-specific syntax overrides.
	 *
	 * <p>
	 * Legacy implementation kept for code written against v1.0.0beta. New code must use
	 * {@link #applySyntaxPalette(Theme, SyntaxPalette)}, which reads the same overrides
	 * from {@link ThemePalette#syntax()}.
	 * @param editorTheme editor theme to update
	 * @param theme active IDE theme
	 * @deprecated since v1.1.0, use {@link #applySyntaxPalette(Theme, SyntaxPalette)}
	 * instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void applyDistinctSyntaxColors(Theme editorTheme, ThemeType theme) {
		LegacySyntaxColors.applyDistinctSyntaxColors(editorTheme, theme);
	}

	/**
	 * Sets the foreground color for the given token types.
	 *
	 * <p>
	 * Legacy helper kept for code written against v1.0.0beta.
	 * @param theme editor theme to update
	 * @param color foreground color
	 * @param tokenTypes token types to update
	 * @deprecated since v1.1.0, use {@link #setSyntaxForeground(Theme, Color, int...)}
	 * instead
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void setForeground(Theme theme, Color color, int... tokenTypes) {
		LegacySyntaxColors.setForeground(theme, color, tokenTypes);
	}

}
