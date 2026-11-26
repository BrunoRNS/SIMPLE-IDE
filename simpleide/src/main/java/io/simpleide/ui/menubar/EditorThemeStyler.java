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

/** Applies the selected syntax theme to one editor instance. */
/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class EditorThemeStyler {

	private EditorThemeStyler() {
	}

	static void apply(Editor editor, ThemeManager.ThemeType theme, java.util.function.Consumer<Editor> applyFont) {
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
				ThemeSyntaxApplier.apply(editorTheme, palette.syntax());
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
				applyFont.accept(editor);
			}
		}
		catch (IOException exception) {
			throw new IllegalStateException("Unable to load editor theme: " + themeResource, exception);
		}

	}

}
