package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
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
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.FontUIResource;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.fife.ui.rtextarea.Gutter;

import io.simpleide.api.HexEditor;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.ui.SyntaxPalette;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorManager;
import io.simpleide.utils.ResourceUtil;

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LegacySyntaxColors {

	private LegacySyntaxColors() {
	}

	static void applyReadableSyntaxColors(Theme editorTheme, ThemeManager.ThemeType theme) {
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

	static void applyDistinctSyntaxColors(Theme editorTheme, ThemeManager.ThemeType theme) {
		if (theme == ThemeManager.ThemeType.BROWN) {
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
		else if (theme == ThemeManager.ThemeType.BROWN_LIGHT) {
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

	static void setForeground(Theme theme, Color color, int... tokenTypes) {
		for (int tokenType : tokenTypes) {
			theme.scheme.getStyle(tokenType).foreground = color;
		}
	}

}
