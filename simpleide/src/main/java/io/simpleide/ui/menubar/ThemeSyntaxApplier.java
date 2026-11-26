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
final class ThemeSyntaxApplier {

	private ThemeSyntaxApplier() {
	}

	static void apply(Theme editorTheme, SyntaxPalette palette) {
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

	static void setSyntaxForeground(Theme theme, Color color, int... tokenTypes) {
		for (int tokenType : tokenTypes) {
			theme.scheme.getStyle(tokenType).foreground = color;
		}
	}

}
