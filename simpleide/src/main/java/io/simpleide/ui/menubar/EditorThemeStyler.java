package io.simpleide.ui.menubar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fife.ui.rsyntaxtextarea.Theme;

import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.editor.Editor;
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
