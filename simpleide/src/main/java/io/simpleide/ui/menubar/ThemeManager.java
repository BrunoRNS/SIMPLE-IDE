package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;

import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.editor.Editor;
import io.simpleide.utils.ResourceUtil;

/**
 * Singleton that applies the application look and feel and editor colors.
 *
 * <p>
 * The manager keeps a set of registered editors so a theme change updates both the Swing
 * windows and the syntax highlighting. Five themes are available: light, dark, Dracula,
 * Brown and Brown Light. Theme changes are marshalled to the Swing Event Dispatch Thread.
 *
 * <p>
 * Covered by {@code ThemeManagerTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
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

		DARK, LIGHT, DRACULA, BROWN, BROWN_LIGHT,

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
				case BROWN -> setupPropertiesLookAndFeel("brown.properties", "Brown");
				case BROWN_LIGHT -> setupPropertiesLookAndFeel("brown-light.properties", "Brown Light");
			}
			applyGlobalUiDefaults(theme);
			updateAllWindows();
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
			window.invalidate();
			window.validate();
			window.repaint();
		}
	}

	private void updateMenuPopups(Component component) {
		if (component == null) {
			return;
		}
		if (component instanceof JMenu menu) {
			SwingUtilities.updateComponentTreeUI(menu.getPopupMenu());
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				updateMenuPopups(child);
			}
		}
	}

	private void applyEditorTheme(Editor editor, ThemeType theme) {
		String themeResource = switch (theme) {
			case LIGHT -> "default.xml";
			case DARK -> "dark.xml";
			case DRACULA -> "dracula.xml";
			case BROWN, BROWN_LIGHT -> "dark.xml";
		};

		try {
			Path themePath = theme == ThemeType.DRACULA
					? ResourceUtil.getResourcePath(ThemeManager.class, themeResource)
					: ResourceUtil.getResourcePath("org.fife.ui.rsyntaxtextarea.themes", themeResource);
			try (InputStream input = Files.newInputStream(themePath)) {
				Theme editorTheme = Theme.load(input);
				if (theme == ThemeType.DRACULA) {
					editorTheme.bgColor = new Color(0x282A36);
					editorTheme.caretColor = new Color(0xF8F8F2);
					editorTheme.selectionBG = new Color(0x44475A);
					editorTheme.currentLineHighlight = new Color(0x44475A);
					editorTheme.gutterBackgroundColor = new Color(0x282A36);
					editorTheme.gutterBorderColor = new Color(0x44475A);
					editorTheme.lineNumberColor = new Color(0x6272A4);
					editorTheme.currentLineNumberColor = new Color(0xF8F8F2);
				}
				if (theme == ThemeType.BROWN || theme == ThemeType.BROWN_LIGHT) {
					boolean lightBrown = theme == ThemeType.BROWN_LIGHT;
					editorTheme.bgColor = new Color(lightBrown ? 0xFBF1C7 : 0x282828);
					editorTheme.caretColor = new Color(lightBrown ? 0x3C3836 : 0xEBDBB2);
					editorTheme.selectionBG = new Color(lightBrown ? 0xD5C4A1 : 0x504945);
					editorTheme.currentLineHighlight = new Color(lightBrown ? 0xF2E5BC : 0x32302F);
					editorTheme.gutterBackgroundColor = editorTheme.bgColor;
					editorTheme.gutterBorderColor = editorTheme.selectionBG;
					editorTheme.lineNumberColor = new Color(lightBrown ? 0xA89984 : 0x928374);
					editorTheme.currentLineNumberColor = editorTheme.caretColor;
				}
				if (theme != ThemeType.DRACULA) {
					applyReadableSyntaxColors(editorTheme, theme);
				}
				editorTheme.apply(editor);
				if (theme == ThemeType.DRACULA) {
					editor.setBracketMatchingEnabled(true);
					editor.setMatchedBracketBGColor(new Color(0x44475A));
					editor.setMatchedBracketBorderColor(new Color(0xFF79C6));
					editor.setPaintMatchedBracketPair(true);
					editor.setMarkOccurrencesColor(new Color(0x44475A));
					editor.setMarkAllHighlightColor(new Color(0x44475A));
					editor.setHyperlinkForeground(new Color(0x8BE9FD));
				}
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

	private void applyReadableSyntaxColors(Theme editorTheme, ThemeType theme) {
		Color comments;
		Color keywords;
		Color types;
		Color functions;
		Color strings;
		Color numbers;
		Color punctuation;
		Color commentKeywords;
		Color identifiers;
		Color variables;
		Color annotations;
		Color regexes;
		Color booleans;
		Color errors;
		Color markupTags;
		Color markupAttributes;
		Color markupValues;
		Color markupOther;

		if (null == theme) {
			comments = new Color(0x8FA39A);
			keywords = new Color(0xF08A9B);
			types = new Color(0x9CCB8A);
			functions = new Color(0x83C5E8);
			strings = new Color(0xE4C07A);
			numbers = new Color(0xC6A0F6);
			punctuation = new Color(0xD5DCE5);
			commentKeywords = new Color(0xE4C07A);
			identifiers = new Color(0xD5DCE5);
			variables = new Color(0x83C5E8);
			annotations = new Color(0xF08A9B);
			regexes = new Color(0xE4C07A);
			booleans = new Color(0xC6A0F6);
			errors = new Color(0xF08A9B);
			markupTags = new Color(0xF08A9B);
			markupAttributes = new Color(0x9CCB8A);
			markupValues = new Color(0xE4C07A);
			markupOther = new Color(0x83C5E8);
		}
		else
			switch (theme) {
				case LIGHT -> {
					comments = new Color(0x687A64);
					keywords = new Color(0xA13D63);
					types = new Color(0x246C72);
					functions = new Color(0x315FA3);
					strings = new Color(0x8A5A00);
					numbers = new Color(0x7952A3);
					punctuation = new Color(0x81511D);
					commentKeywords = new Color(0x8A5A00);
					identifiers = new Color(0x315FA3);
					variables = new Color(0x246C72);
					annotations = new Color(0xA13D63);
					regexes = new Color(0x7952A3);
					booleans = new Color(0x7952A3);
					errors = new Color(0xB00020);
					markupTags = new Color(0xA13D63);
					markupAttributes = new Color(0x246C72);
					markupValues = new Color(0x8A5A00);
					markupOther = new Color(0x7952A3);
				}
				case BROWN -> {
					comments = new Color(0x928374);
					keywords = new Color(0xFB4934);
					types = new Color(0xFABD2F);
					functions = new Color(0xB8BB26);
					strings = new Color(0xB8BB26);
					numbers = new Color(0xD3869B);
					punctuation = new Color(0xEBDBB2);
					commentKeywords = new Color(0xFE8019);
					identifiers = new Color(0x83A598);
					variables = new Color(0x8EC07C);
					annotations = new Color(0xFE8019);
					regexes = new Color(0x83A598);
					booleans = new Color(0xD3869B);
					errors = new Color(0xFB4934);
					markupTags = new Color(0xFB4934);
					markupAttributes = new Color(0xFABD2F);
					markupValues = new Color(0xB8BB26);
					markupOther = new Color(0x83A598);
				}
				case BROWN_LIGHT -> {
					comments = new Color(0xA89984);
					keywords = new Color(0x9D0006);
					types = new Color(0xB57614);
					functions = new Color(0x79740E);
					strings = new Color(0x79740E);
					numbers = new Color(0x8F3F71);
					punctuation = new Color(0x3C3836);
					commentKeywords = new Color(0xAF3A03);
					identifiers = new Color(0x076678);
					variables = new Color(0x427B58);
					annotations = new Color(0xAF3A03);
					regexes = new Color(0x076678);
					booleans = new Color(0x8F3F71);
					errors = new Color(0x9D0006);
					markupTags = new Color(0x9D0006);
					markupAttributes = new Color(0xB57614);
					markupValues = new Color(0x79740E);
					markupOther = new Color(0x076678);
				}
				default -> {
					comments = new Color(0x8FA39A);
					keywords = new Color(0xF08A9B);
					types = new Color(0x9CCB8A);
					functions = new Color(0x83C5E8);
					strings = new Color(0xE4C07A);
					numbers = new Color(0xC6A0F6);
					punctuation = new Color(0xD5DCE5);
					commentKeywords = new Color(0xE4C07A);
					identifiers = new Color(0xD5DCE5);
					variables = new Color(0x83C5E8);
					annotations = new Color(0xF08A9B);
					regexes = new Color(0xE4C07A);
					booleans = new Color(0xC6A0F6);
					errors = new Color(0xF08A9B);
					markupTags = new Color(0xF08A9B);
					markupAttributes = new Color(0x9CCB8A);
					markupValues = new Color(0xE4C07A);
					markupOther = new Color(0x83C5E8);
				}
			}

		setForeground(editorTheme, comments, TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE,
				TokenTypes.COMMENT_DOCUMENTATION);
		setForeground(editorTheme, commentKeywords, TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP);
		setForeground(editorTheme, keywords, TokenTypes.RESERVED_WORD, TokenTypes.RESERVED_WORD_2);
		setForeground(editorTheme, types, TokenTypes.DATA_TYPE);
		setForeground(editorTheme, functions, TokenTypes.FUNCTION);
		setForeground(editorTheme, identifiers, TokenTypes.IDENTIFIER);
		setForeground(editorTheme, variables, TokenTypes.VARIABLE);
		setForeground(editorTheme, strings, TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, TokenTypes.LITERAL_CHAR,
				TokenTypes.LITERAL_BACKQUOTE);
		setForeground(editorTheme, numbers, TokenTypes.LITERAL_NUMBER_DECIMAL_INT, TokenTypes.LITERAL_NUMBER_FLOAT,
				TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
		setForeground(editorTheme, booleans, TokenTypes.LITERAL_BOOLEAN);
		setForeground(editorTheme, annotations, TokenTypes.ANNOTATION);
		setForeground(editorTheme, regexes, TokenTypes.REGEX);
		setForeground(editorTheme, errors, TokenTypes.ERROR_IDENTIFIER, TokenTypes.ERROR_NUMBER_FORMAT,
				TokenTypes.ERROR_STRING_DOUBLE, TokenTypes.ERROR_CHAR);
		setForeground(editorTheme, markupTags, TokenTypes.MARKUP_TAG_DELIMITER, TokenTypes.MARKUP_TAG_NAME,
				TokenTypes.MARKUP_PROCESSING_INSTRUCTION, TokenTypes.MARKUP_CDATA_DELIMITER);
		setForeground(editorTheme, markupAttributes, TokenTypes.MARKUP_TAG_ATTRIBUTE);
		setForeground(editorTheme, markupValues, TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE);
		setForeground(editorTheme, markupOther, TokenTypes.MARKUP_DTD, TokenTypes.MARKUP_ENTITY_REFERENCE,
				TokenTypes.MARKUP_CDATA);
		setForeground(editorTheme, comments, TokenTypes.MARKUP_COMMENT);
		setForeground(editorTheme, punctuation, TokenTypes.OPERATOR, TokenTypes.SEPARATOR, TokenTypes.PREPROCESSOR);
		applyDistinctSyntaxColors(editorTheme, theme);
	}

	private void applyDistinctSyntaxColors(Theme editorTheme, ThemeType theme) {
		if (theme == ThemeType.BROWN) {
			setForeground(editorTheme, new Color(0xA89984), TokenTypes.COMMENT_MULTILINE);
			setForeground(editorTheme, new Color(0xBDAE93), TokenTypes.COMMENT_DOCUMENTATION);
			setForeground(editorTheme, new Color(0xFE8019), TokenTypes.COMMENT_MARKUP);
			setForeground(editorTheme, new Color(0xD3869B), TokenTypes.RESERVED_WORD_2);
			setForeground(editorTheme, new Color(0xE0A577), TokenTypes.LITERAL_NUMBER_FLOAT);
			setForeground(editorTheme, new Color(0xD3869B), TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
			setForeground(editorTheme, new Color(0xFE8019), TokenTypes.LITERAL_CHAR);
			setForeground(editorTheme, new Color(0x83A598), TokenTypes.LITERAL_BACKQUOTE);
			setForeground(editorTheme, new Color(0xFE8019), TokenTypes.ERROR_NUMBER_FORMAT);
			setForeground(editorTheme, new Color(0xFB4934), TokenTypes.ERROR_STRING_DOUBLE);
			setForeground(editorTheme, new Color(0xFF6B5E), TokenTypes.ERROR_CHAR);
			setForeground(editorTheme, new Color(0xE4685D), TokenTypes.MARKUP_TAG_NAME);
			setForeground(editorTheme, new Color(0xD3869B), TokenTypes.MARKUP_PROCESSING_INSTRUCTION);
			setForeground(editorTheme, new Color(0x83A598), TokenTypes.MARKUP_CDATA_DELIMITER);
			setForeground(editorTheme, new Color(0xD3869B), TokenTypes.MARKUP_DTD);
			setForeground(editorTheme, new Color(0x8EC07C), TokenTypes.MARKUP_ENTITY_REFERENCE);
			setForeground(editorTheme, new Color(0x83A598), TokenTypes.MARKUP_CDATA);
		}
		else if (theme == ThemeType.BROWN_LIGHT) {
			setForeground(editorTheme, new Color(0x928374), TokenTypes.COMMENT_MULTILINE);
			setForeground(editorTheme, new Color(0x7C6F64), TokenTypes.COMMENT_DOCUMENTATION);
			setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.COMMENT_MARKUP);
			setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.RESERVED_WORD_2);
			setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.LITERAL_NUMBER_FLOAT);
			setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
			setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.LITERAL_CHAR);
			setForeground(editorTheme, new Color(0x076678), TokenTypes.LITERAL_BACKQUOTE);
			setForeground(editorTheme, new Color(0xAF3A03), TokenTypes.ERROR_NUMBER_FORMAT);
			setForeground(editorTheme, new Color(0x9D0006), TokenTypes.ERROR_STRING_DOUBLE);
			setForeground(editorTheme, new Color(0xB00020), TokenTypes.ERROR_CHAR);
			setForeground(editorTheme, new Color(0xA31218), TokenTypes.MARKUP_TAG_NAME);
			setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.MARKUP_PROCESSING_INSTRUCTION);
			setForeground(editorTheme, new Color(0x076678), TokenTypes.MARKUP_CDATA_DELIMITER);
			setForeground(editorTheme, new Color(0x8F3F71), TokenTypes.MARKUP_DTD);
			setForeground(editorTheme, new Color(0x427B58), TokenTypes.MARKUP_ENTITY_REFERENCE);
			setForeground(editorTheme, new Color(0x076678), TokenTypes.MARKUP_CDATA);
		}
	}

	private void setForeground(Theme theme, Color color, int... tokenTypes) {
		for (int tokenType : tokenTypes) {
			theme.scheme.getStyle(tokenType).foreground = color;
		}
	}

	private void applyGlobalUiDefaults(ThemeType theme) {
		Palette p = paletteFromCurrentLookAndFeel(theme);
		UIDefaults d = UIManager.getLookAndFeelDefaults();

		put(d, "control", p.bg);
		put(d, "controlText", p.fg);
		put(d, "text", p.bg);
		put(d, "textText", p.fg);
		put(d, "info", p.popupBg);
		put(d, "infoText", p.fg);
		put(d, "nimbusBase", p.bg);
		put(d, "nimbusLightBackground", p.bg);

		put(d, "Panel.background", p.bg);
		put(d, "Panel.foreground", p.fg);
		put(d, "Label.foreground", p.fg);
		put(d, "Label.background", p.bg);
		put(d, "Label.disabledForeground", p.disabledFg);
		put(d, "TitledBorder.titleColor", p.fg);
		put(d, "Separator.foreground", p.border);
		put(d, "Separator.background", p.bg);
		put(d, "Viewport.background", p.bg);
		put(d, "ScrollPane.background", p.bg);
		put(d, "ScrollPane.foreground", p.fg);

		putTextLike(d, "TextField", p.inputBg, p.fg, p.selBg, p.selFg, p.caret, p.disabledFg);
		putTextLike(d, "FormattedTextField", p.inputBg, p.fg, p.selBg, p.selFg, p.caret, p.disabledFg);
		putTextLike(d, "PasswordField", p.inputBg, p.fg, p.selBg, p.selFg, p.caret, p.disabledFg);
		putTextLike(d, "TextArea", p.bg, p.fg, p.selBg, p.selFg, p.caret, p.disabledFg);
		putTextLike(d, "TextPane", p.bg, p.fg, p.selBg, p.selFg, p.caret, p.disabledFg);
		putTextLike(d, "EditorPane", p.bg, p.fg, p.selBg, p.selFg, p.caret, p.disabledFg);

		put(d, "MenuBar.background", p.bg);
		put(d, "MenuBar.foreground", p.fg);
		put(d, "MenuBar.borderColor", p.border);
		put(d, "MenuBar.hoverBackground", p.menuSelBg);
		put(d, "MenuBar.hoverForeground", p.menuSelFg);
		put(d, "MenuBar.selectionBackground", p.menuSelBg);
		put(d, "MenuBar.selectionForeground", p.menuSelFg);
		put(d, "MenuBar.disabledForeground", p.disabledFg);
		putMenuLike(d, "Menu", p.menuBg, p.menuFg, p.menuSelBg, p.menuSelFg, p.disabledFg);
		putMenuLike(d, "MenuItem", p.menuBg, p.menuFg, p.menuSelBg, p.menuSelFg, p.disabledFg);
		putMenuLike(d, "CheckBoxMenuItem", p.menuBg, p.menuFg, p.menuSelBg, p.menuSelFg, p.disabledFg);
		putMenuLike(d, "RadioButtonMenuItem", p.menuBg, p.menuFg, p.menuSelBg, p.menuSelFg, p.disabledFg);
		put(d, "PopupMenu.background", p.popupBg);
		put(d, "PopupMenu.foreground", p.popupFg);
		put(d, "PopupMenu.borderColor", p.border);

		put(d, "Button.foreground", p.buttonFg);
		put(d, "Button.background", p.buttonBg);
		put(d, "Button.disabledText", p.disabledFg);
		put(d, "Button.disabledForeground", p.disabledFg);
		put(d, "ToggleButton.foreground", p.buttonFg);
		put(d, "ToggleButton.background", p.buttonBg);
		put(d, "ToggleButton.disabledText", p.disabledFg);
		put(d, "CheckBox.foreground", p.fg);
		put(d, "CheckBox.background", p.bg);
		put(d, "RadioButton.foreground", p.fg);
		put(d, "RadioButton.background", p.bg);
		put(d, "ComboBox.foreground", p.fg);
		put(d, "ComboBox.background", p.inputBg);
		put(d, "ComboBox.selectionBackground", p.selBg);
		put(d, "ComboBox.selectionForeground", p.selFg);
		put(d, "ComboBox.disabledForeground", p.disabledFg);
		put(d, "Spinner.foreground", p.fg);
		put(d, "Spinner.background", p.inputBg);

		put(d, "List.background", p.bg);
		put(d, "List.foreground", p.fg);
		put(d, "List.selectionBackground", p.selBg);
		put(d, "List.selectionForeground", p.selFg);
		put(d, "Table.background", p.bg);
		put(d, "Table.foreground", p.fg);
		put(d, "Table.selectionBackground", p.selBg);
		put(d, "Table.selectionForeground", p.selFg);
		put(d, "Table.gridColor", p.border);
		put(d, "TableHeader.background", p.headerBg);
		put(d, "TableHeader.foreground", p.headerFg);
		put(d, "Tree.background", p.bg);
		put(d, "Tree.foreground", p.fg);
		put(d, "Tree.textBackground", p.bg);
		put(d, "Tree.textForeground", p.fg);
		put(d, "Tree.selectionBackground", p.selBg);
		put(d, "Tree.selectionForeground", p.selFg);

		put(d, "TabbedPane.foreground", p.fg);
		put(d, "TabbedPane.background", p.bg);
		put(d, "TabbedPane.contentAreaColor", p.bg);
		put(d, "TabbedPane.selectedBackground", p.bg);
		put(d, "TabbedPane.selectedForeground", p.fg);
		put(d, "TabbedPane.disabledForeground", p.disabledFg);
		put(d, "TabbedPane.hoverColor", p.selBg);
		put(d, "TabbedPane.focusColor", p.selBg);
		put(d, "TabbedPane.underlineColor", p.selBg);
		put(d, "TabbedPane.inactiveUnderlineColor", p.border);
		put(d, "ToolBar.foreground", p.fg);
		put(d, "ToolBar.background", p.bg);
		put(d, "ToolTip.background", p.popupBg);
		put(d, "ToolTip.foreground", p.popupFg);

		put(d, "OptionPane.background", p.bg);
		put(d, "OptionPane.foreground", p.fg);
		put(d, "OptionPane.messageForeground", p.fg);
		put(d, "OptionPane.buttonBackground", p.buttonBg);
		put(d, "OptionPane.buttonForeground", p.buttonFg);

		put(d, "ProgressBar.background", p.headerBg);
		put(d, "ProgressBar.foreground", p.selBg);
		put(d, "ProgressBar.selectionForeground", p.selFg);
		put(d, "ProgressBar.selectionBackground", p.fg);
		put(d, "Slider.background", p.bg);
		put(d, "Slider.foreground", p.fg);
		put(d, "FileChooser.background", p.bg);
		put(d, "FileChooser.foreground", p.fg);
	}

	private void putTextLike(UIDefaults d, String prefix, Color bg, Color fg, Color selBg, Color selFg, Color caret,
			Color disabledFg) {
		put(d, prefix + ".background", bg);
		put(d, prefix + ".foreground", fg);
		put(d, prefix + ".caretForeground", caret);
		put(d, prefix + ".selectionBackground", selBg);
		put(d, prefix + ".selectionForeground", selFg);
		put(d, prefix + ".inactiveForeground", disabledFg);
		put(d, prefix + ".inactiveBackground", bg);
		put(d, prefix + ".disabledBackground", bg);
	}

	private void putMenuLike(UIDefaults d, String prefix, Color bg, Color fg, Color selBg, Color selFg,
			Color disabledFg) {
		put(d, prefix + ".background", bg);
		put(d, prefix + ".foreground", fg);
		put(d, prefix + ".selectionBackground", selBg);
		put(d, prefix + ".selectionForeground", selFg);
		put(d, prefix + ".disabledForeground", disabledFg);
		put(d, prefix + ".acceleratorForeground", fg);
		put(d, prefix + ".acceleratorSelectionForeground", selFg);
	}

	private void put(UIDefaults d, String key, Object value) {
		if (value != null) {
			d.put(key, value);
		}
	}

	private Palette paletteFromCurrentLookAndFeel(ThemeType theme) {
		ThemeDefaults td = themeDefaults(theme);
		Color bg = first(td.bg, "Panel.background", "control", "window", "Viewport.background");
		Color fg = ensureContrast(first(td.fg, "Label.foreground", "Panel.foreground", "controlText", "textText"), bg);
		Color inputBg = first(td.inputBg, "TextField.background", "TextArea.background", "TextPane.background", "text");
		if (inputBg == null) {
			inputBg = bg;
		}
		Color selBg = first(td.selBg, "MenuItem.selectionBackground", "TextField.selectionBackground",
				"List.selectionBackground", "Table.selectionBackground");
		if (selBg == null) {
			selBg = blend(fg, bg, 0.25f);
		}
		Color selFg = ensureContrast(first(td.selFg, "MenuItem.selectionForeground", "TextField.selectionForeground",
				"List.selectionForeground", "Table.selectionForeground"), selBg);
		Color disabledFg = first(td.disabledFg, "Label.disabledForeground", "MenuItem.disabledForeground",
				"Button.disabledText");
		if (disabledFg == null || contrastRatio(disabledFg, bg) < 3.0d) {
			disabledFg = blend(fg, bg, 0.45f);
		}
		if (contrastRatio(disabledFg, bg) < 3.0d) {
			disabledFg = ensureContrast(disabledFg, bg);
		}
		Color popupBg = first(td.popupBg, "PopupMenu.background", "MenuItem.background", "ToolTip.background");
		if (popupBg == null) {
			popupBg = bg;
		}
		Color border = first(td.border, "Component.borderColor", "MenuBar.borderColor", "Separator.foreground");
		if (border == null) {
			border = blend(fg, bg, 0.25f);
		}
		Color caret = ensureContrast(first(td.caret, "TextField.caretForeground", "TextComponent.caretForeground"),
				inputBg);
		Color menuBg = first("Menu.background", "MenuItem.background", "MenuBar.background");
		if (menuBg == null) {
			menuBg = bg;
		}
		Color menuFg = ensureContrast(first("Menu.foreground", "MenuItem.foreground", "MenuBar.foreground"), menuBg);
		Color menuSelBg = first("Menu.selectionBackground", "MenuItem.selectionBackground");
		if (menuSelBg == null) {
			menuSelBg = selBg;
		}
		Color menuSelFg = ensureContrast(first("Menu.selectionForeground", "MenuItem.selectionForeground"), menuSelBg);
		Color popupFg = ensureContrast(first("PopupMenu.foreground", "ToolTip.foreground"), popupBg);
		Color buttonBg = first("Button.background", "OptionPane.buttonBackground", "ToggleButton.background");
		if (buttonBg == null) {
			buttonBg = bg;
		}
		Color buttonFg = ensureContrast(
				first("Button.foreground", "OptionPane.buttonForeground", "ToggleButton.foreground"), buttonBg);
		Color headerBg = first("TableHeader.background", "ToolBar.background");
		if (headerBg == null) {
			headerBg = bg;
		}
		Color headerFg = ensureContrast(first("TableHeader.foreground", "ToolBar.foreground"), headerBg);

		return new Palette(bg, fg, inputBg, buttonBg, buttonFg, selBg, selFg, disabledFg, popupBg, popupFg, menuBg,
				menuFg, menuSelBg, menuSelFg, border, caret, headerBg, headerFg);
	}

	private ThemeDefaults themeDefaults(ThemeType theme) {
		return switch (theme) {
			case DRACULA -> new ThemeDefaults(c(0x282A36), c(0xF8F8F2), c(0x282A36), c(0x44475A), c(0xF8F8F2),
					c(0x6272A4), c(0x21222C), c(0x44475A), c(0xF8F8F2));
			case BROWN -> new ThemeDefaults(c(0x282828), c(0xEBDBB2), c(0x3C3836), c(0x504945), c(0xFBF1C7),
					c(0x928374), c(0x282828), c(0x504945), c(0xEBDBB2));
			case BROWN_LIGHT -> new ThemeDefaults(c(0xFBF1C7), c(0x3C3836), c(0xF2E5BC), c(0xD5C4A1), c(0x282828),
					c(0x7C6F64), c(0xFBF1C7), c(0xD5C4A1), c(0x3C3836));
			case DARK -> new ThemeDefaults(c(0x3C3F41), c(0xBBBBBB), null, c(0x4B6EAF), c(0xFFFFFF), c(0x777777), null,
					null, c(0xBBBBBB));
			case LIGHT -> new ThemeDefaults(c(0xF2F2F2), c(0x1F1F1F), null, c(0x2675BF), c(0xFFFFFF), c(0x8C8C8C), null,
					null, c(0x1F1F1F));
		};
	}

	private static Color first(Color preferred, String... keys) {
		if (preferred != null) {
			return preferred;
		}
		for (String key : keys) {
			Color color = UIManager.getColor(key);
			if (color != null) {
				return color;
			}
		}
		return null;
	}

	private static Color first(String... keys) {
		return first(null, keys);
	}

	private static Color c(int rgb) {
		return new Color(rgb);
	}

	private static Color ensureContrast(Color fg, Color bg) {
		if (bg == null) {
			return fg;
		}
		if (fg == null) {
			return contrastingForeground(bg);
		}
		if (contrastRatio(fg, bg) >= 4.5d) {
			return fg;
		}
		Color contrasted = contrastingForeground(bg);
		if (contrastRatio(contrasted, bg) >= 4.5d) {
			return contrasted;
		}
		return contrastRatio(Color.WHITE, bg) >= contrastRatio(Color.BLACK, bg) ? Color.WHITE : Color.BLACK;
	}

	private static Color contrastingForeground(Color bg) {
		return contrastRatio(Color.WHITE, bg) >= contrastRatio(Color.BLACK, bg) ? Color.WHITE : Color.BLACK;
	}

	private static double contrastRatio(Color c1, Color c2) {
		if (c1 == null || c2 == null) {
			return 21.0d;
		}
		double l1 = luminance(c1);
		double l2 = luminance(c2);
		double lighter = Math.max(l1, l2);
		double darker = Math.min(l1, l2);
		return (lighter + 0.05d) / (darker + 0.05d);
	}

	private static double luminance(Color c) {
		double r = srgb(c.getRed());
		double g = srgb(c.getGreen());
		double b = srgb(c.getBlue());
		return 0.2126d * r + 0.7152d * g + 0.0722d * b;
	}

	private static double srgb(int channel) {
		double v = channel / 255.0d;
		return v <= 0.04045d ? v / 12.92d : Math.pow((v + 0.055d) / 1.055d, 2.4d);
	}

	private static Color blend(Color fg, Color bg, float fgRatio) {
		if (fg == null) {
			return bg;
		}
		if (bg == null) {
			return fg;
		}
		float bgRatio = 1f - fgRatio;
		int r = Math.round(fg.getRed() * fgRatio + bg.getRed() * bgRatio);
		int g = Math.round(fg.getGreen() * fgRatio + bg.getGreen() * bgRatio);
		int b = Math.round(fg.getBlue() * fgRatio + bg.getBlue() * bgRatio);
		return new Color(clamp(r), clamp(g), clamp(b));
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}

	private record ThemeDefaults(Color bg, Color fg, Color inputBg, Color selBg, Color selFg, Color disabledFg,
			Color popupBg, Color border, Color caret) {
	}

	private record Palette(Color bg, Color fg, Color inputBg, Color buttonBg, Color buttonFg, Color selBg, Color selFg,
			Color disabledFg, Color popupBg, Color popupFg, Color menuBg, Color menuFg, Color menuSelBg,
			Color menuSelFg, Color border, Color caret, Color headerBg, Color headerFg) {
	}

}
