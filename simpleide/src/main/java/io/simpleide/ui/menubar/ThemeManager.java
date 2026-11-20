package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.SyntaxPalette;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorManager;
import io.simpleide.utils.ResourceUtil;

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
 * @author LuisOtavio13
 * @since v1.1.0
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/menubar.md">docs/api/ui/menubar.md</a>
 */
public final class ThemeManager {

	private static final ThemeManager INSTANCE = new ThemeManager();

	private ThemeType currentTheme = ThemeType.DARK;

	private final List<Editor> editors = new ArrayList<>();

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

	/**
	 * Selects a theme and applies it to all windows and registered editors.
	 * @param newTheme theme to apply
	 */
	public void setTheme(ThemeType newTheme) {
		this.currentTheme = newTheme;
		applyTheme(newTheme);
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
			applyGlobalUiDefaults(theme);
			updateAllWindows();
			EditorManager.refreshFileIcons();
			for (Editor registeredEditor : editors) {
				if (registeredEditor != null) {
					applyEditorTheme(registeredEditor, theme);
				}
			}
		});
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
			menuItem.setForeground(palette.foreground());
			if (menuItem instanceof JMenu menu && menu.getParent() instanceof JMenuBar) {
				menu.setBackground(palette.menuBarBackground());
				menu.setOpaque(true);
			}
		}
		else if (component instanceof AbstractButton button) {
			button.putClientProperty("JButton.buttonType", "roundRect");
			button.setBackground(palette.buttonBackground());
			button.setForeground(palette.buttonForeground());
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
	}

	private void updateMenuPopups(Component component) {
		if (component == null) {
			return;
		}
		if (component instanceof JPopupMenu popup) {
			SwingUtilities.updateComponentTreeUI(popup);
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
		ThemePalette palette = ThemePalette.of(theme);
		String themeResource = palette.dark() ? "dark.xml" : "default.xml";

		try {
			Path themePath = ResourceUtil.getResourcePath("org.fife.ui.rsyntaxtextarea.themes", themeResource);
			try (InputStream input = Files.newInputStream(themePath)) {
				Theme editorTheme = Theme.load(input);
				editorTheme.bgColor = palette.background();
				editorTheme.caretColor = palette.foreground();
				editorTheme.selectionBG = palette.editorSelectionBackground();
				editorTheme.selectionFG = palette.editorSelectionForeground();
				editorTheme.useSelectionFG = true;
				editorTheme.selectionRoundedEdges = true;
				editorTheme.currentLineHighlight = palette.editorCurrentLine();
				editorTheme.fadeCurrentLineHighlight = false;
				editorTheme.gutterBackgroundColor = palette.editorGutterBackground();
				editorTheme.gutterBorderColor = palette.border();
				editorTheme.lineNumberColor = palette.editorLineNumber();
				editorTheme.currentLineNumberColor = palette.editorCurrentLineNumber();
				editorTheme.marginLineColor = palette.border();
				editorTheme.markAllHighlightColor = palette.editorSelectionBackground();
				editorTheme.markOccurrencesColor = palette.editorSelectionBackground();
				editorTheme.markOccurrencesBorder = false;
				editorTheme.matchedBracketFG = palette.accent();
				editorTheme.matchedBracketBG = palette.editorSelectionBackground();
				editorTheme.matchedBracketHighlightBoth = true;
				editorTheme.matchedBracketAnimate = true;
				editorTheme.hyperlinkFG = palette.syntax().hyperlink();
				editorTheme.activeLineRangeColor = palette.accent();
				editorTheme.tabLineColor = palette.border();
				editorTheme.foldIndicatorBG = palette.surface();
				editorTheme.foldIndicatorFG = palette.mutedForeground();
				editorTheme.foldIndicatorArmedFG = palette.accent();
				editorTheme.foldBG = palette.surface();
				editorTheme.armedFoldBG = palette.elevated();
				editorTheme.iconRowHeaderInheritsGutterBG = true;
				applySyntaxPalette(editorTheme, palette.syntax());
				editorTheme.apply(editor);
				editor.setBracketMatchingEnabled(true);
				editor.setMatchedBracketBGColor(palette.editorSelectionBackground());
				editor.setMatchedBracketBorderColor(palette.accent());
				editor.setPaintMatchedBracketPair(true);
				editor.setMarkOccurrencesColor(palette.editorSelectionBackground());
				editor.setMarkAllHighlightColor(palette.editorSelectionBackground());
				editor.setHyperlinkForeground(palette.syntax().hyperlink());
				editor.setTabLineColor(palette.border());
				editor.setSelectionColor(palette.editorSelectionBackground());
				editor.setSelectedTextColor(palette.editorSelectionForeground());
				editor.setUseSelectedTextColor(true);
				editor.setCaretColor(palette.foreground());
				editor.setBackground(palette.background());
				editor.setForeground(palette.foreground());
			}
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to load editor theme: " + themeResource, exception);
		}
	}

	private void setupPropertiesLookAndFeel(String resourceName, String themeName) {
		try (InputStream input = ThemeManager.class.getResourceAsStream(resourceName)) {
			if (input == null) {
				throw new IllegalStateException("Unable to load " + themeName + " look and feel properties");
			}
			UIManager.setLookAndFeel(new FlatPropertiesLaf(themeName, input));
		}
		catch (IOException | UnsupportedLookAndFeelException exception) {
			throw new IllegalStateException("Unable to apply " + themeName + " look and feel", exception);
		}
	}

	private void applySyntaxPalette(Theme editorTheme, SyntaxPalette palette) {
		for (int tokenType = 0; tokenType < editorTheme.scheme.getStyleCount(); tokenType++) {
			editorTheme.scheme.getStyle(tokenType).foreground = palette.punctuation();
		}
		setSyntaxForeground(editorTheme, palette.comment(), TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE,
				TokenTypes.COMMENT_DOCUMENTATION, TokenTypes.MARKUP_COMMENT);
		setSyntaxForeground(editorTheme, palette.commentKeyword(), TokenTypes.COMMENT_KEYWORD,
				TokenTypes.COMMENT_MARKUP);
		setSyntaxForeground(editorTheme, palette.keyword(), TokenTypes.RESERVED_WORD);
		setSyntaxForeground(editorTheme, palette.keywordSecondary(), TokenTypes.RESERVED_WORD_2);
		setSyntaxForeground(editorTheme, palette.type(), TokenTypes.DATA_TYPE);
		setSyntaxForeground(editorTheme, palette.function(), TokenTypes.FUNCTION);
		setSyntaxForeground(editorTheme, palette.string(), TokenTypes.LITERAL_STRING_DOUBLE_QUOTE,
				TokenTypes.LITERAL_CHAR, TokenTypes.LITERAL_BACKQUOTE);
		setSyntaxForeground(editorTheme, palette.number(), TokenTypes.LITERAL_NUMBER_DECIMAL_INT,
				TokenTypes.LITERAL_NUMBER_FLOAT, TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
		setSyntaxForeground(editorTheme, palette.booleanLiteral(), TokenTypes.LITERAL_BOOLEAN);
		setSyntaxForeground(editorTheme, palette.annotation(), TokenTypes.ANNOTATION);
		setSyntaxForeground(editorTheme, palette.regex(), TokenTypes.REGEX);
		setSyntaxForeground(editorTheme, palette.identifier(), TokenTypes.IDENTIFIER);
		setSyntaxForeground(editorTheme, palette.variable(), TokenTypes.VARIABLE);
		setSyntaxForeground(editorTheme, palette.operator(), TokenTypes.OPERATOR);
		setSyntaxForeground(editorTheme, palette.separator(), TokenTypes.SEPARATOR);
		setSyntaxForeground(editorTheme, palette.preprocessor(), TokenTypes.PREPROCESSOR);
		setSyntaxForeground(editorTheme, palette.error(), TokenTypes.ERROR_IDENTIFIER, TokenTypes.ERROR_NUMBER_FORMAT,
				TokenTypes.ERROR_STRING_DOUBLE, TokenTypes.ERROR_CHAR);
		setSyntaxForeground(editorTheme, palette.markupTag(), TokenTypes.MARKUP_TAG_DELIMITER,
				TokenTypes.MARKUP_TAG_NAME, TokenTypes.MARKUP_PROCESSING_INSTRUCTION,
				TokenTypes.MARKUP_CDATA_DELIMITER);
		setSyntaxForeground(editorTheme, palette.markupAttribute(), TokenTypes.MARKUP_TAG_ATTRIBUTE);
		setSyntaxForeground(editorTheme, palette.markupValue(), TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE);
		setSyntaxForeground(editorTheme, palette.markupOther(), TokenTypes.MARKUP_DTD,
				TokenTypes.MARKUP_ENTITY_REFERENCE, TokenTypes.MARKUP_CDATA);
		for (int tokenType = 0; tokenType < editorTheme.scheme.getStyleCount(); tokenType++) {
			var style = editorTheme.scheme.getStyle(tokenType);
			if (style.foreground == null) {
				style.foreground = palette.punctuation();
			}
			style.background = null;
		}
	}

	private void setSyntaxForeground(Theme theme, Color color, int... tokenTypes) {
		for (int tokenType : tokenTypes) {
			theme.scheme.getStyle(tokenType).foreground = color;
		}
	}

	private void applyGlobalUiDefaults(ThemeType theme) {
		ThemePalette p = ThemePalette.of(theme);
		SyntaxPalette syntax = p.syntax();
		UIDefaults d = UIManager.getLookAndFeelDefaults();

		put(d, "Component.accentColor", p.accent());
		put(d, "Component.focusColor", alpha(p.accent(), 0x33));
		put(d, "Component.borderColor", p.border());
		put(d, "Component.disabledBorderColor", p.border());
		put(d, "Component.focusedBorderColor", p.focusBorder());
		put(d, "Component.error.borderColor", syntax.error());
		put(d, "Component.error.focusedBorderColor", syntax.error());
		put(d, "Component.warning.borderColor", syntax.string());
		put(d, "Component.warning.focusedBorderColor", syntax.string());
		put(d, "Component.success.borderColor", syntax.function());
		put(d, "Component.success.focusedBorderColor", syntax.function());
		put(d, "Component.focusWidth", 1);
		put(d, "Component.innerFocusWidth", 1);
		put(d, "Component.borderWidth", 1);
		put(d, "Component.arc", 12);
		put(d, "Component.minimumWidth", 0);
		put(d, "Component.arrowType", "chevron");
		put(d, "Component.hideMnemonics", true);

		put(d, "control", p.surface());
		put(d, "controlText", p.foreground());
		put(d, "controlShadow", p.border());
		put(d, "controlDkShadow", p.border());
		put(d, "controlHighlight", p.surface());
		put(d, "controlLtHighlight", p.surface());
		put(d, "text", p.background());
		put(d, "textText", p.foreground());
		put(d, "textHighlight", p.selectionBackground());
		put(d, "textHighlightText", p.selectionForeground());
		put(d, "textInactiveText", p.disabledForeground());
		put(d, "info", p.tooltipBackground());
		put(d, "infoText", p.tooltipForeground());
		put(d, "menu", p.popupBackground());
		put(d, "menuText", p.popupForeground());
		put(d, "window", p.background());
		put(d, "windowText", p.foreground());
		put(d, "desktop", p.surface());
		put(d, "activeCaption", p.surface());
		put(d, "activeCaptionText", p.foreground());
		put(d, "inactiveCaption", p.surface());
		put(d, "inactiveCaptionText", p.disabledForeground());
		put(d, "scrollbar", p.scrollbarTrack());

		put(d, "Panel.background", p.background());
		put(d, "Panel.foreground", p.foreground());
		put(d, "Label.background", p.background());
		put(d, "Label.foreground", p.foreground());
		put(d, "Label.disabledForeground", p.disabledForeground());
		put(d, "TitledBorder.titleColor", p.foreground());
		put(d, "TitledBorder.borderColor", p.border());
		put(d, "Separator.foreground", p.border());
		put(d, "Separator.background", p.background());
		put(d, "Separator.height", 1);
		put(d, "Separator.stripeWidth", 1);
		put(d, "Separator.stripeIndent", 1);

		put(d, "SplitPane.background", p.background());
		put(d, "SplitPaneDivider.background", p.background());
		put(d, "SplitPane.dividerSize", 10);
		put(d, "SplitPaneDivider.style", "plain");
		put(d, "SplitPaneDivider.gripColor", p.mutedForeground());
		put(d, "SplitPaneDivider.hoverColor", p.accent());
		put(d, "SplitPaneDivider.pressedColor", p.accent());
		put(d, "SplitPaneDivider.draggingColor", p.accent());
		put(d, "SplitPaneDivider.oneTouchArrowColor", p.foreground());
		put(d, "SplitPaneDivider.oneTouchHoverArrowColor", p.accent());
		put(d, "SplitPaneDivider.oneTouchPressedArrowColor", p.accent());
		put(d, "SplitPaneDivider.gripDotCount", 3);
		put(d, "SplitPaneDivider.gripDotSize", 4);
		put(d, "SplitPaneDivider.gripGap", 2);

		put(d, "Viewport.background", p.background());
		put(d, "ScrollPane.background", p.background());
		put(d, "ScrollPane.foreground", p.foreground());
		put(d, "ScrollPane.borderColor", p.border());
		put(d, "ScrollPane.arc", 10);
		put(d, "ScrollPane.fillUpperCorner", true);
		put(d, "ScrollPane.smoothScrolling", true);
		put(d, "ScrollBar.track", p.scrollbarTrack());
		put(d, "ScrollBar.thumb", p.scrollbarThumb());
		put(d, "ScrollBar.hoverThumbColor", p.scrollbarHoverThumb());
		put(d, "ScrollBar.pressedThumbColor", p.scrollbarHoverThumb());
		put(d, "ScrollBar.hoverTrackColor", p.scrollbarTrack());
		put(d, "ScrollBar.pressedTrackColor", p.scrollbarTrack());
		put(d, "ScrollBar.width", 10);
		put(d, "ScrollBar.thumbArc", 999);
		put(d, "ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
		put(d, "ScrollBar.trackArc", 0);
		put(d, "ScrollBar.trackInsets", new Insets(0, 0, 0, 0));
		put(d, "ScrollBar.showButtons", false);
		put(d, "ScrollBar.buttonArrowColor", p.foreground());
		put(d, "ScrollBar.buttonDisabledArrowColor", p.disabledForeground());
		put(d, "ScrollBar.hoverButtonBackground", p.surface());
		put(d, "ScrollBar.pressedButtonBackground", p.surface());

		putTextLike(d, "TextField", p.inputBackground(), p.inputForeground(), p.selectionBackground(),
				p.selectionForeground(), p.inputForeground(), p.disabledForeground(), p.border(), p.focusBorder(),
				new Insets(2, 6, 2, 6));
		putTextLike(d, "FormattedTextField", p.inputBackground(), p.inputForeground(), p.selectionBackground(),
				p.selectionForeground(), p.inputForeground(), p.disabledForeground(), p.border(), p.focusBorder(),
				new Insets(2, 6, 2, 6));
		putTextLike(d, "PasswordField", p.inputBackground(), p.inputForeground(), p.selectionBackground(),
				p.selectionForeground(), p.inputForeground(), p.disabledForeground(), p.border(), p.focusBorder(),
				new Insets(2, 6, 2, 6));
		putTextLike(d, "TextArea", p.background(), p.foreground(), p.selectionBackground(), p.selectionForeground(),
				p.foreground(), p.disabledForeground(), p.border(), p.focusBorder(), new Insets(2, 6, 2, 6));
		putTextLike(d, "TextPane", p.background(), p.foreground(), p.selectionBackground(), p.selectionForeground(),
				p.foreground(), p.disabledForeground(), p.border(), p.focusBorder(), new Insets(2, 6, 2, 6));
		putTextLike(d, "EditorPane", p.background(), p.foreground(), p.selectionBackground(), p.selectionForeground(),
				p.foreground(), p.disabledForeground(), p.border(), p.focusBorder(), new Insets(2, 6, 2, 6));
		put(d, "TextField.placeholderForeground", p.mutedForeground());
		put(d, "FormattedTextField.placeholderForeground", p.mutedForeground());
		put(d, "PasswordField.placeholderForeground", p.mutedForeground());
		put(d, "TextField.arc", 10);
		put(d, "FormattedTextField.arc", 10);
		put(d, "PasswordField.arc", 10);
		put(d, "TextArea.arc", 8);
		put(d, "TextPane.arc", 8);
		put(d, "EditorPane.arc", 8);

		put(d, "MenuBar.background", p.menuBarBackground());
		put(d, "MenuBar.foreground", p.foreground());
		put(d, "MenuBar.disabledForeground", p.disabledForeground());
		put(d, "MenuBar.borderColor", p.border());
		put(d, "MenuBar.hoverBackground", p.selectionBackground());
		put(d, "MenuBar.hoverForeground", p.selectionForeground());
		put(d, "MenuBar.selectionBackground", p.selectionBackground());
		put(d, "MenuBar.selectionForeground", p.selectionForeground());
		put(d, "MenuBar.itemMargins", new Insets(3, 8, 3, 8));
		put(d, "MenuBar.selectionInsets", new Insets(2, 6, 2, 6));
		put(d, "MenuBar.selectionArc", 8);
		putMenuLike(d, "Menu", p);
		putMenuLike(d, "MenuItem", p);
		putMenuLike(d, "CheckBoxMenuItem", p);
		putMenuLike(d, "RadioButtonMenuItem", p);
		put(d, "MenuItem.underlineSelectionBackground", p.menuSelectionBackground());
		put(d, "MenuItem.underlineSelectionColor", p.accent());
		put(d, "MenuItem.underlineSelectionHeight", 2);
		put(d, "Menu.arrowType", "chevron");
		put(d, "MenuItem.arrowType", "chevron");
		put(d, "CheckBoxMenuItem.arrowType", "chevron");
		put(d, "RadioButtonMenuItem.arrowType", "chevron");
		put(d, "CheckBoxMenuItem.icon.checkmarkColor", p.menuSelectionForeground());
		put(d, "RadioButtonMenuItem.icon.checkmarkColor", p.menuSelectionForeground());

		put(d, "Popup.background", p.popupBackground());
		put(d, "Popup.foreground", p.popupForeground());
		put(d, "Popup.borderColor", p.border());
		put(d, "Popup.borderWidth", 1);
		put(d, "Popup.borderCornerRadius", 12);
		put(d, "Popup.dropShadowPainted", true);
		put(d, "PopupMenu.background", p.popupBackground());
		put(d, "PopupMenu.foreground", p.popupForeground());
		put(d, "PopupMenu.borderColor", p.border());
		put(d, "PopupMenu.borderWidth", 1);
		put(d, "PopupMenu.borderCornerRadius", 12);
		put(d, "PopupMenu.borderInsets", new Insets(4, 1, 4, 1));
		put(d, "PopupMenu.separatorColor", p.border());
		put(d, "PopupMenuSeparator.stripeWidth", 1);
		put(d, "PopupMenuSeparator.stripeIndent", 4);
		put(d, "PopupMenuSeparator.height", 9);
		put(d, "ToolTip.background", p.tooltipBackground());
		put(d, "ToolTip.foreground", p.tooltipForeground());
		put(d, "ToolTip.borderColor", p.border());
		put(d, "ToolTip.borderWidth", 1);
		put(d, "ToolTip.borderCornerRadius", 10);
		put(d, "ToolTip.margin", new Insets(4, 8, 4, 8));

		put(d, "Button.background", p.buttonBackground());
		put(d, "Button.foreground", p.buttonForeground());
		put(d, "Button.disabledBackground", p.background());
		put(d, "Button.disabledText", p.disabledForeground());
		put(d, "Button.disabledForeground", p.disabledForeground());
		put(d, "Button.hoverBackground", p.buttonHoverBackground());
		put(d, "Button.pressedBackground", p.buttonPressedBackground());
		put(d, "Button.selectedBackground", p.buttonPressedBackground());
		put(d, "Button.selectedForeground", p.buttonForeground());
		put(d, "Button.default.background", p.accent());
		put(d, "Button.default.foreground", p.buttonForeground());
		put(d, "Button.default.hoverBackground", p.buttonHoverBackground());
		put(d, "Button.default.pressedBackground", p.buttonPressedBackground());
		put(d, "Button.default.borderColor", p.accent());
		put(d, "Button.default.focusedBorderColor", p.focusBorder());
		put(d, "Button.borderColor", p.border());
		put(d, "Button.focusedBorderColor", p.focusBorder());
		put(d, "Button.hoverBorderColor", p.focusBorder());
		put(d, "Button.disabledBorderColor", p.border());
		put(d, "Button.arc", 12);
		put(d, "Button.borderWidth", 1);
		put(d, "Button.margin", new Insets(2, 14, 2, 14));
		put(d, "Button.minimumWidth", 72);
		put(d, "Button.iconTextGap", 4);
		put(d, "Button.toolbar.hoverBackground", p.buttonHoverBackground());
		put(d, "Button.toolbar.pressedBackground", p.buttonPressedBackground());
		put(d, "Button.toolbar.selectedBackground", p.buttonPressedBackground());
		put(d, "ToggleButton.background", p.buttonBackground());
		put(d, "ToggleButton.foreground", p.buttonForeground());
		put(d, "ToggleButton.hoverBackground", p.buttonHoverBackground());
		put(d, "ToggleButton.pressedBackground", p.buttonPressedBackground());
		put(d, "ToggleButton.selectedBackground", p.accent());
		put(d, "ToggleButton.selectedForeground", p.buttonForeground());
		put(d, "ToggleButton.disabledBackground", p.background());
		put(d, "ToggleButton.disabledText", p.disabledForeground());
		put(d, "ToggleButton.disabledForeground", p.disabledForeground());
		put(d, "ToggleButton.disabledSelectedBackground", p.buttonBackground());
		putCheckBoxLike(d, "CheckBox", p);
		putCheckBoxLike(d, "RadioButton", p);

		put(d, "ComboBox.background", p.inputBackground());
		put(d, "ComboBox.foreground", p.inputForeground());
		put(d, "ComboBox.disabledBackground", p.inputBackground());
		put(d, "ComboBox.disabledForeground", p.disabledForeground());
		put(d, "ComboBox.buttonBackground", p.inputBackground());
		put(d, "ComboBox.buttonEditableBackground", p.inputBackground());
		put(d, "ComboBox.buttonSeparatorColor", p.border());
		put(d, "ComboBox.buttonDisabledSeparatorColor", p.border());
		put(d, "ComboBox.buttonArrowColor", p.foreground());
		put(d, "ComboBox.buttonDisabledArrowColor", p.disabledForeground());
		put(d, "ComboBox.buttonHoverArrowColor", p.accent());
		put(d, "ComboBox.buttonPressedArrowColor", p.accent());
		put(d, "ComboBox.selectionBackground", p.selectionBackground());
		put(d, "ComboBox.selectionForeground", p.selectionForeground());
		put(d, "ComboBox.popupBackground", p.popupBackground());
		put(d, "ComboBox.popupForeground", p.popupForeground());
		put(d, "ComboBox.arc", 10);
		put(d, "ComboBox.borderColor", p.border());
		put(d, "ComboBox.focusedBorderColor", p.focusBorder());
		put(d, "ComboBox.disabledBorderColor", p.border());
		put(d, "ComboBox.editableBackground", p.inputBackground());
		put(d, "ComboBox.focusedBackground", p.inputBackground());
		put(d, "ComboBox.selectionInsets", new Insets(2, 4, 2, 4));
		put(d, "ComboBox.selectionArc", 8);
		put(d, "ComboBox.padding", new Insets(2, 6, 2, 6));
		put(d, "ComboBox.minimumWidth", 72);
		put(d, "ComboBox.arrowType", "chevron");

		put(d, "Spinner.background", p.inputBackground());
		put(d, "Spinner.foreground", p.inputForeground());
		put(d, "Spinner.disabledBackground", p.inputBackground());
		put(d, "Spinner.disabledForeground", p.disabledForeground());
		put(d, "Spinner.buttonBackground", p.inputBackground());
		put(d, "Spinner.buttonSeparatorColor", p.border());
		put(d, "Spinner.buttonDisabledSeparatorColor", p.border());
		put(d, "Spinner.buttonArrowColor", p.foreground());
		put(d, "Spinner.buttonDisabledArrowColor", p.disabledForeground());
		put(d, "Spinner.buttonHoverArrowColor", p.accent());
		put(d, "Spinner.buttonPressedArrowColor", p.accent());
		put(d, "Spinner.borderColor", p.border());
		put(d, "Spinner.focusedBorderColor", p.focusBorder());
		put(d, "Spinner.disabledBorderColor", p.border());
		put(d, "Spinner.arc", 10);
		put(d, "Spinner.padding", new Insets(2, 6, 2, 6));
		put(d, "Spinner.buttonStyle", "button");

		put(d, "List.background", p.background());
		put(d, "List.foreground", p.foreground());
		put(d, "List.selectionBackground", p.selectionBackground());
		put(d, "List.selectionForeground", p.selectionForeground());
		put(d, "List.selectionInactiveBackground", p.inactiveSelectionBackground());
		put(d, "List.selectionInactiveForeground", p.inactiveSelectionForeground());
		put(d, "List.cellFocusColor", p.focusBorder());
		put(d, "List.selectionArc", 8);
		put(d, "List.selectionInsets", new Insets(2, 4, 2, 4));
		put(d, "List.cellMargins", new Insets(2, 6, 2, 6));
		put(d, "List.rowHeight", 22);
		put(d, "Table.background", p.background());
		put(d, "Table.foreground", p.foreground());
		put(d, "Table.selectionBackground", p.selectionBackground());
		put(d, "Table.selectionForeground", p.selectionForeground());
		put(d, "Table.selectionInactiveBackground", p.inactiveSelectionBackground());
		put(d, "Table.selectionInactiveForeground", p.inactiveSelectionForeground());
		put(d, "Table.gridColor", p.border());
		put(d, "Table.showHorizontalLines", false);
		put(d, "Table.showVerticalLines", false);
		put(d, "Table.intercellSpacing", new java.awt.Dimension(0, 0));
		put(d, "Table.rowHeight", 22);
		put(d, "Table.cellFocusColor", p.focusBorder());
		put(d, "Table.cellMargins", new Insets(2, 6, 2, 6));
		put(d, "Table.focusCellBackground", p.background());
		put(d, "Table.focusCellForeground", p.foreground());
		put(d, "TableHeader.background", p.headerBackground());
		put(d, "TableHeader.foreground", p.headerForeground());
		put(d, "TableHeader.disabledForeground", p.disabledForeground());
		put(d, "TableHeader.hoverBackground", p.selectionBackground());
		put(d, "TableHeader.hoverForeground", p.selectionForeground());
		put(d, "TableHeader.pressedBackground", p.selectionBackground());
		put(d, "TableHeader.pressedForeground", p.selectionForeground());
		put(d, "TableHeader.separatorColor", p.border());
		put(d, "TableHeader.bottomSeparatorColor", p.border());
		put(d, "TableHeader.height", 26);
		put(d, "TableHeader.cellMargins", new Insets(2, 6, 2, 6));
		put(d, "TableHeader.sortIconColor", p.mutedForeground());
		put(d, "TableHeader.focusCellBackground", p.headerBackground());
		put(d, "Tree.background", p.background());
		put(d, "Tree.foreground", p.foreground());
		put(d, "Tree.textBackground", p.background());
		put(d, "Tree.textForeground", p.foreground());
		put(d, "Tree.selectionBackground", p.treeSelectionBackground());
		put(d, "Tree.selectionForeground", p.selectionForeground());
		put(d, "Tree.selectionInactiveBackground", p.inactiveSelectionBackground());
		put(d, "Tree.selectionInactiveForeground", p.inactiveSelectionForeground());
		put(d, "Tree.selectionBorderColor", p.treeSelectionBackground());
		put(d, "Tree.hash", p.border());
		put(d, "Tree.selectionArc", 8);
		put(d, "Tree.selectionInsets", new Insets(2, 4, 2, 4));
		put(d, "Tree.rowHeight", 22);
		put(d, "Tree.rendererMargins", new Insets(2, 4, 2, 4));
		put(d, "Tree.paintLines", false);
		put(d, "Tree.showDefaultIcons", false);
		put(d, "Tree.wideSelection", true);
		put(d, "Tree.dropLineColor", p.accent());
		put(d, "Tree.dropCellBackground", p.treeSelectionBackground());
		put(d, "Tree.dropCellForeground", p.selectionForeground());
		put(d, "Tree.icon.expandedColor", p.mutedForeground());
		put(d, "Tree.icon.collapsedColor", p.mutedForeground());
		put(d, "Tree.icon.leafColor", p.mutedForeground());
		put(d, "Tree.icon.closedColor", p.mutedForeground());
		put(d, "Tree.icon.openColor", p.mutedForeground());

		put(d, "TabbedPane.foreground", p.foreground());
		put(d, "TabbedPane.background", p.surface());
		put(d, "TabbedPane.selectedForeground", p.tabSelectedForeground());
		put(d, "TabbedPane.selectedBackground", p.tabSelectedBackground());
		put(d, "TabbedPane.disabledForeground", p.disabledForeground());
		put(d, "TabbedPane.disabledBackground", p.surface());
		put(d, "TabbedPane.hoverForeground", p.foreground());
		put(d, "TabbedPane.hoverColor", p.selectionBackground());
		put(d, "TabbedPane.focusColor", p.selectionBackground());
		put(d, "TabbedPane.underlineColor", p.tabUnderline());
		put(d, "TabbedPane.inactiveUnderlineColor", p.border());
		put(d, "TabbedPane.disabledUnderlineColor", p.border());
		put(d, "TabbedPane.contentAreaColor", p.background());
		put(d, "TabbedPane.tabSeparatorColor", p.border());
		put(d, "TabbedPane.showTabSeparators", true);
		put(d, "TabbedPane.tabSeparatorsFullHeight", false);
		put(d, "TabbedPane.showContentSeparator", true);
		put(d, "TabbedPane.tabHeight", 32);
		put(d, "TabbedPane.tabSelectionHeight", 2);
		put(d, "TabbedPane.tabInsets", new Insets(4, 12, 4, 12));
		put(d, "TabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
		put(d, "TabbedPane.tabArc", 10);
		put(d, "TabbedPane.tabSelectionArc", 10);
		put(d, "TabbedPane.tabType", "underlined");
		put(d, "TabbedPane.hasFullBorder", false);

		put(d, "ToolBar.background", p.surface());
		put(d, "ToolBar.foreground", p.foreground());
		put(d, "ToolBar.borderColor", p.border());
		put(d, "ToolBar.hoverBackground", p.buttonHoverBackground());
		put(d, "ToolBar.pressedBackground", p.buttonPressedBackground());
		put(d, "ToolBar.hoverForeground", p.buttonForeground());
		put(d, "ToolBar.pressedForeground", p.buttonForeground());
		put(d, "ToolBar.separatorColor", p.border());
		put(d, "ToolBar.separatorWidth", 1);
		put(d, "ToolBar.margin", new Insets(2, 2, 2, 2));
		put(d, "ToolBar.spacingInsets", new Insets(2, 2, 2, 2));
		put(d, "ToolBar.gripColor", p.mutedForeground());

		put(d, "OptionPane.background", p.surface());
		put(d, "OptionPane.foreground", p.foreground());
		put(d, "OptionPane.messageForeground", p.foreground());
		put(d, "OptionPane.buttonBackground", p.buttonBackground());
		put(d, "OptionPane.buttonForeground", p.buttonForeground());
		put(d, "OptionPane.border", BorderFactory.createEmptyBorder(12, 12, 12, 12));
		put(d, "OptionPane.messageAreaBorder", BorderFactory.createEmptyBorder(0, 0, 0, 0));
		put(d, "OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(12, 0, 0, 0));
		put(d, "FileChooser.background", p.surface());
		put(d, "FileChooser.foreground", p.foreground());
		put(d, "FileChooser.borderColor", p.border());
		put(d, "FileChooser.focusColor", p.focusBorder());
		put(d, "FileChooser.readOnlyForeground", p.mutedForeground());
		put(d, "ColorChooser.background", p.surface());
		put(d, "ColorChooser.foreground", p.foreground());
		put(d, "ColorChooser.swatchBorderColor", p.border());
		put(d, "ColorChooser.swatchSelectedBorderColor", p.accent());
		put(d, "ColorChooser.swatchFocusColor", p.focusBorder());
		put(d, "ColorChooser.swatchesDefaultRecentColor", p.surface());

		put(d, "ProgressBar.background", p.surface());
		put(d, "ProgressBar.foreground", p.accent());
		put(d, "ProgressBar.selectionBackground", p.foreground());
		put(d, "ProgressBar.selectionForeground", p.background());
		put(d, "ProgressBar.arc", 6);
		put(d, "ProgressBar.horizontalSize", new java.awt.Dimension(146, 4));
		put(d, "ProgressBar.verticalSize", new java.awt.Dimension(4, 146));
		put(d, "Slider.background", p.background());
		put(d, "Slider.foreground", p.foreground());
		put(d, "Slider.thumbColor", p.accent());
		put(d, "Slider.trackColor", p.border());
		put(d, "Slider.trackValueColor", p.accent());
		put(d, "Slider.hoverThumbColor", p.accent());
		put(d, "Slider.pressedThumbColor", p.accent());
		put(d, "Slider.disabledTrackColor", p.border());
		put(d, "Slider.disabledThumbColor", p.disabledForeground());
		put(d, "Slider.tickColor", p.mutedForeground());
		put(d, "Slider.focusColor", alpha(p.accent(), 0x33));
		put(d, "Slider.trackWidth", 4);
		put(d, "Slider.thumbSize", new java.awt.Dimension(14, 14));
		put(d, "Slider.thumbArc", 999);
		put(d, "Slider.trackArc", 999);

		put(d, "TitlePane.background", p.menuBarBackground());
		put(d, "TitlePane.inactiveBackground", p.surface());
		put(d, "TitlePane.foreground", p.foreground());
		put(d, "TitlePane.inactiveForeground", p.disabledForeground());
		put(d, "TitlePane.buttonHoverBackground", p.selectionBackground());
		put(d, "TitlePane.buttonPressedBackground", p.buttonPressedBackground());
		put(d, "TitlePane.buttonForeground", p.foreground());
		put(d, "TitlePane.buttonInactiveForeground", p.disabledForeground());
		put(d, "TitlePane.buttonHoverForeground", p.selectionForeground());
		put(d, "TitlePane.buttonPressedForeground", p.selectionForeground());
		put(d, "InternalFrame.background", p.surface());
		put(d, "InternalFrame.foreground", p.foreground());
		put(d, "InternalFrame.borderColor", p.border());
		put(d, "InternalFrame.activeBorderColor", p.focusBorder());
		put(d, "InternalFrame.inactiveBorderColor", p.border());
		put(d, "InternalFrame.activeTitleBackground", p.menuBarBackground());
		put(d, "InternalFrame.activeTitleForeground", p.foreground());
		put(d, "InternalFrame.inactiveTitleBackground", p.surface());
		put(d, "InternalFrame.inactiveTitleForeground", p.disabledForeground());
		put(d, "InternalFrame.buttonHoverBackground", p.selectionBackground());
		put(d, "InternalFrame.buttonPressedBackground", p.buttonPressedBackground());
		put(d, "DesktopPane.background", p.surface());
		put(d, "RootPane.background", p.background());
		put(d, "RootPane.activeBorderColor", p.focusBorder());
		put(d, "RootPane.inactiveBorderColor", p.border());

		put(d, "Log.traceForeground", p.mutedForeground());
		put(d, "Log.debugForeground", syntax.type());
		put(d, "Log.infoForeground", p.foreground());
		put(d, "Log.warnForeground", syntax.string());
		put(d, "Log.errorForeground", syntax.error());
	}

	private void putTextLike(UIDefaults d, String prefix, Color background, Color foreground, Color selectionBackground,
			Color selectionForeground, Color caret, Color disabledForeground, Color border, Color focusBorder,
			Insets margin) {
		put(d, prefix + ".background", background);
		put(d, prefix + ".foreground", foreground);
		put(d, prefix + ".caretForeground", caret);
		put(d, prefix + ".selectionBackground", selectionBackground);
		put(d, prefix + ".selectionForeground", selectionForeground);
		put(d, prefix + ".inactiveBackground", background);
		put(d, prefix + ".inactiveForeground", foreground);
		put(d, prefix + ".disabledBackground", background);
		put(d, prefix + ".disabledForeground", disabledForeground);
		put(d, prefix + ".borderColor", border);
		put(d, prefix + ".focusedBorderColor", focusBorder);
		put(d, prefix + ".disabledBorderColor", border);
		put(d, prefix + ".margin", margin);
	}

	private void putMenuLike(UIDefaults d, String prefix, ThemePalette p) {
		put(d, prefix + ".background", p.popupBackground());
		put(d, prefix + ".foreground", p.popupForeground());
		put(d, prefix + ".selectionBackground", p.menuSelectionBackground());
		put(d, prefix + ".selectionForeground", p.menuSelectionForeground());
		put(d, prefix + ".disabledForeground", p.disabledForeground());
		put(d, prefix + ".acceleratorForeground", p.mutedForeground());
		put(d, prefix + ".acceleratorSelectionForeground", p.menuSelectionForeground());
		put(d, prefix + ".selectionInsets", new Insets(2, 4, 2, 4));
		put(d, prefix + ".selectionArc", 8);
		put(d, prefix + ".margin", new Insets(3, 6, 3, 6));
	}

	private void putCheckBoxLike(UIDefaults d, String prefix, ThemePalette p) {
		put(d, prefix + ".background", p.background());
		put(d, prefix + ".foreground", p.foreground());
		put(d, prefix + ".disabledText", p.disabledForeground());
		put(d, prefix + ".disabledForeground", p.disabledForeground());
		put(d, prefix + ".icon.background", p.inputBackground());
		put(d, prefix + ".icon.borderColor", p.border());
		put(d, prefix + ".icon.selectedBorderColor", p.accent());
		put(d, prefix + ".icon.selectedBackground", p.accent());
		put(d, prefix + ".icon.checkmarkColor", p.buttonForeground());
		put(d, prefix + ".icon.focusedBorderColor", p.focusBorder());
		put(d, prefix + ".icon.hoverBorderColor", p.focusBorder());
		put(d, prefix + ".icon.pressedBorderColor", p.focusBorder());
		put(d, prefix + ".icon.disabledBorderColor", p.border());
		put(d, prefix + ".icon.disabledBackground", p.background());
		put(d, prefix + ".icon.disabledCheckmarkColor", p.disabledForeground());
		put(d, prefix + ".icon.focusWidth", 1);
		put(d, prefix + ".icon.arc", 4);
	}

	private void put(UIDefaults d, String key, Object value) {
		if (value != null) {
			d.put(key, value);
			UIManager.put(key, value);
		}
	}

	private static Color alpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

}
