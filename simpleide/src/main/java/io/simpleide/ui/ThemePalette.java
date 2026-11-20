package io.simpleide.ui;

import io.simpleide.ui.menubar.ThemeManager;
import java.awt.Color;

/**
 * Complete color palette for one application theme.
 *
 * <p>
 * Palettes follow the official colors of VS Code Dark+ and Light+, Dracula and Alucard,
 * and Gruvbox dark and light. {@link io.simpleide.ui.menubar.ThemeManager} applies these
 * values directly to the look and feel defaults and to the editor, so every surface,
 * border, selection, icon and syntax token uses the original theme colors instead of a
 * contrast-adjusted approximation.
 *
 * @param type theme identifier
 * @param dark whether the theme is dark
 * @param background editor and general content background
 * @param surface panels, toolbars, tabs and dialogs
 * @param elevated menus, popups and tooltips
 * @param menuBarBackground menu bar background
 * @param foreground primary text
 * @param mutedForeground secondary text and icons
 * @param disabledForeground disabled text
 * @param border widget, separator and divider border
 * @param focusBorder focused border
 * @param selectionBackground active selection
 * @param selectionForeground text on the active selection
 * @param inactiveSelectionBackground inactive selection
 * @param inactiveSelectionForeground text on the inactive selection
 * @param accent accent color
 * @param inputBackground text input background
 * @param inputForeground text input foreground
 * @param buttonBackground button background
 * @param buttonHoverBackground hovered button background
 * @param buttonPressedBackground pressed button background
 * @param buttonForeground button text
 * @param headerBackground table header and tab strip background
 * @param headerForeground table header and tab strip text
 * @param scrollbarTrack scrollbar track
 * @param scrollbarThumb scrollbar thumb
 * @param scrollbarHoverThumb hovered scrollbar thumb
 * @param popupBackground popup menu background
 * @param popupForeground popup menu text
 * @param tooltipBackground tooltip background
 * @param tooltipForeground tooltip text
 * @param editorGutterBackground editor gutter background
 * @param editorLineNumber line number color
 * @param editorCurrentLineNumber current line number color
 * @param editorCurrentLine current line highlight
 * @param editorSelectionBackground editor selection background
 * @param editorSelectionForeground editor selection text
 * @param tabBackground unselected tab background
 * @param tabSelectedBackground selected tab background
 * @param tabSelectedForeground selected tab text
 * @param tabUnderline selected tab underline
 * @param menuSelectionBackground menu item selection background
 * @param menuSelectionForeground menu item selection text
 * @param syntax syntax highlighting colors
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public record ThemePalette(ThemeManager.ThemeType type, boolean dark, Color background, Color surface, Color elevated,
		Color menuBarBackground, Color foreground, Color mutedForeground, Color disabledForeground, Color border,
		Color focusBorder, Color selectionBackground, Color selectionForeground, Color inactiveSelectionBackground,
		Color inactiveSelectionForeground, Color accent, Color inputBackground, Color inputForeground,
		Color buttonBackground, Color buttonHoverBackground, Color buttonPressedBackground, Color buttonForeground,
		Color headerBackground, Color headerForeground, Color scrollbarTrack, Color scrollbarThumb,
		Color scrollbarHoverThumb, Color popupBackground, Color popupForeground, Color tooltipBackground,
		Color tooltipForeground, Color editorGutterBackground, Color editorLineNumber, Color editorCurrentLineNumber,
		Color editorCurrentLine, Color editorSelectionBackground, Color editorSelectionForeground, Color tabBackground,
		Color tabSelectedBackground, Color tabSelectedForeground, Color tabUnderline, Color menuSelectionBackground,
		Color menuSelectionForeground, SyntaxPalette syntax) {

	/**
	 * Returns the official palette for a theme.
	 * @param type theme identifier
	 * @return the palette for the theme
	 */
	public static ThemePalette of(ThemeManager.ThemeType type) {
		return switch (type) {
			case DARK -> darkPalette();
			case LIGHT -> lightPalette();
			case DRACULA -> draculaPalette();
			case DRACULA_LIGHT -> draculaLightPalette();
			case BROWN -> brownPalette();
			case BROWN_LIGHT -> brownLightPalette();
		};
	}

	private static ThemePalette darkPalette() {
		return new ThemePalette(ThemeManager.ThemeType.DARK, true, c(0x1E1E1E), c(0x252526), c(0x252526), c(0x2D2D30),
				c(0xCCCCCC), c(0x9D9D9D), c(0x9D9D9D), c(0x3C3C3C), c(0x007ACC), c(0x094771), c(0xFFFFFF), c(0x37373D),
				c(0xCCCCCC), c(0x007ACC), c(0x3C3C3C), c(0xCCCCCC), c(0x0E639C), c(0x1177BB), c(0x0D5A8F), c(0xFFFFFF),
				c(0x252526), c(0xCCCCCC), c(0x1E1E1E), c(0x424242), c(0x4F4F4F), c(0x252526), c(0xCCCCCC), c(0x252526),
				c(0xCCCCCC), c(0x1E1E1E), c(0x858585), c(0xC6C6C6), c(0x282828), c(0x264F78), c(0xFFFFFF), c(0x252526),
				c(0x1E1E1E), c(0xFFFFFF), c(0x007ACC), c(0x094771), c(0xFFFFFF),
				new SyntaxPalette(c(0x6A9955), c(0x569CD6), c(0xC586C0), c(0x569CD6), c(0x4EC9B0), c(0xDCDCAA),
						c(0xCE9178), c(0xB5CEA8), c(0x569CD6), c(0xD4D4D4), c(0xD4D4D4), c(0xD4D4D4), c(0x9CDCFE),
						c(0x9CDCFE), c(0xDCDCAA), c(0xD16969), c(0xF44747), c(0x569CD6), c(0x9CDCFE), c(0xCE9178),
						c(0xD4D4D4), c(0xC586C0), c(0x3794FF)));
	}

	private static ThemePalette lightPalette() {
		return new ThemePalette(ThemeManager.ThemeType.LIGHT, false, c(0xFFFFFF), c(0xF3F3F3), c(0xFFFFFF), c(0xF3F3F3),
				c(0x333333), c(0x717171), c(0x717171), c(0xE5E5E5), c(0x0090F1), c(0xADD6FF), c(0x000000), c(0xE4E6F1),
				c(0x333333), c(0x007ACC), c(0xFFFFFF), c(0x333333), c(0x007ACC), c(0x0062A3), c(0x005A9E), c(0xFFFFFF),
				c(0xF3F3F3), c(0x333333), c(0xFFFFFF), c(0xC1C1C1), c(0xA8A8A8), c(0xFFFFFF), c(0x333333), c(0xF3F3F3),
				c(0x333333), c(0xFFFFFF), c(0x237893), c(0x0B216F), c(0xF3F3F3), c(0xADD6FF), c(0x000000), c(0xF3F3F3),
				c(0xFFFFFF), c(0x333333), c(0x007ACC), c(0xADD6FF), c(0x000000),
				new SyntaxPalette(c(0x008000), c(0x0000FF), c(0xAF00DB), c(0x0000FF), c(0x267F99), c(0x795E26),
						c(0xA31515), c(0x098658), c(0x0000FF), c(0x000000), c(0x000000), c(0x000000), c(0x001080),
						c(0x001080), c(0x795E26), c(0x811F3F), c(0xE51400), c(0x800000), c(0xFF0000), c(0x0000FF),
						c(0x000000), c(0xAF00DB), c(0x0066BF)));
	}

	private static ThemePalette draculaPalette() {
		return new ThemePalette(ThemeManager.ThemeType.DRACULA, true, c(0x282A36), c(0x282A36), c(0x21222C),
				c(0x282A36), c(0xF8F8F2), c(0x6272A4), c(0x6272A4), c(0x44475A), c(0xBD93F9), c(0x44475A), c(0xF8F8F2),
				c(0x343746), c(0xF8F8F2), c(0x6272A4), c(0x282A36), c(0xF8F8F2), c(0x44475A), c(0x6272A4), c(0x343746),
				c(0xF8F8F2), c(0x21222C), c(0xF8F8F2), c(0x282A36), c(0x44475A), c(0x6272A4), c(0x21222C), c(0xF8F8F2),
				c(0x21222C), c(0xF8F8F2), c(0x282A36), c(0x6272A4), c(0xF8F8F2), c(0x44475A), c(0x44475A), c(0xF8F8F2),
				c(0x21222C), c(0x282A36), c(0xF8F8F2), c(0xBD93F9), c(0x44475A), c(0xF8F8F2),
				new SyntaxPalette(c(0x6272A4), c(0xFF79C6), c(0xFF79C6), c(0xBD93F9), c(0x8BE9FD), c(0x50FA7B),
						c(0xF1FA8C), c(0xBD93F9), c(0xBD93F9), c(0xF8F8F2), c(0xF8F8F2), c(0xFF79C6), c(0x8BE9FD),
						c(0xFFB86C), c(0x50FA7B), c(0xFF79C6), c(0xFF5555), c(0xFF79C6), c(0x50FA7B), c(0xF1FA8C),
						c(0x8BE9FD), c(0xFF79C6), c(0x8BE9FD)));
	}

	private static ThemePalette draculaLightPalette() {
		return new ThemePalette(ThemeManager.ThemeType.DRACULA_LIGHT, false, c(0xFFFBEB), c(0xF3EEDF), c(0xFFFFFF),
				c(0xF3EEDF), c(0x1F1F1F), c(0x6C664B), c(0x6C664B), c(0xE2DDF0), c(0x644AC9), c(0xDCD5F2), c(0x1F1F1F),
				c(0xE5E0D0), c(0x1F1F1F), c(0x644AC9), c(0xFFFFFF), c(0x1F1F1F), c(0xE5E0D0), c(0xD8D2BF), c(0xCFC7B0),
				c(0x1F1F1F), c(0xF3EEDF), c(0x1F1F1F), c(0xFFFBEB), c(0xCFCFDE), c(0xB8B4C8), c(0xFFFFFF), c(0x1F1F1F),
				c(0xFFFFFF), c(0x1F1F1F), c(0xFFFBEB), c(0x6C664B), c(0x1F1F1F), c(0xF3EEDF), c(0xCFCFDE), c(0x1F1F1F),
				c(0xF3EEDF), c(0xFFFBEB), c(0x1F1F1F), c(0x644AC9), c(0xCFCFDE), c(0x1F1F1F),
				new SyntaxPalette(c(0x6C664B), c(0x644AC9), c(0xA3144D), c(0x644AC9), c(0x036A96), c(0x14710A),
						c(0xA34D14), c(0x644AC9), c(0x644AC9), c(0x1F1F1F), c(0x1F1F1F), c(0x1F1F1F), c(0x1F1F1F),
						c(0x036A96), c(0x14710A), c(0xA3144D), c(0xCB3A2A), c(0xA3144D), c(0x14710A), c(0xA34D14),
						c(0x036A96), c(0xA3144D), c(0x036A96)));
	}

	private static ThemePalette brownPalette() {
		return new ThemePalette(ThemeManager.ThemeType.BROWN, true, c(0x282828), c(0x32302F), c(0x1D2021), c(0x282828),
				c(0xEBDBB2), c(0x928374), c(0x928374), c(0x504945), c(0xD79921), c(0x504945), c(0xFBF1C7), c(0x3C3836),
				c(0xEBDBB2), c(0xD79921), c(0x3C3836), c(0xEBDBB2), c(0x3C3836), c(0x504945), c(0x665C54), c(0xEBDBB2),
				c(0x32302F), c(0xEBDBB2), c(0x282828), c(0x665C54), c(0x928374), c(0x1D2021), c(0xEBDBB2), c(0x3C3836),
				c(0xEBDBB2), c(0x282828), c(0x928374), c(0xEBDBB2), c(0x32302F), c(0x504945), c(0xFBF1C7), c(0x282828),
				c(0x32302F), c(0xEBDBB2), c(0xD79921), c(0x504945), c(0xFBF1C7),
				new SyntaxPalette(c(0x928374), c(0xFE8019), c(0xFB4934), c(0xD3869B), c(0xFABD2F), c(0xB8BB26),
						c(0xB8BB26), c(0xD3869B), c(0xD3869B), c(0xEBDBB2), c(0xEBDBB2), c(0xEBDBB2), c(0x83A598),
						c(0x8EC07C), c(0xFE8019), c(0x83A598), c(0xFB4934), c(0xFB4934), c(0xFABD2F), c(0xB8BB26),
						c(0x83A598), c(0xFE8019), c(0x83A598)));
	}

	private static ThemePalette brownLightPalette() {
		return new ThemePalette(ThemeManager.ThemeType.BROWN_LIGHT, false, c(0xFBF1C7), c(0xF2E5BC), c(0xFFFFFF),
				c(0xFBF1C7), c(0x3C3836), c(0x7C6F64), c(0x7C6F64), c(0xE6D9B8), c(0xB57614), c(0xE5D8B8), c(0x282828),
				c(0xEBDBB2), c(0x3C3836), c(0xB57614), c(0xF2E5BC), c(0x3C3836), c(0xEBDBB2), c(0xD5C4A1), c(0xBDAE93),
				c(0x3C3836), c(0xF2E5BC), c(0x3C3836), c(0xFBF1C7), c(0xBDAE93), c(0xA89984), c(0xFFFFFF), c(0x3C3836),
				c(0xF2E5BC), c(0x3C3836), c(0xFBF1C7), c(0xA89984), c(0x3C3836), c(0xF2E5BC), c(0xD5C4A1), c(0x282828),
				c(0xFBF1C7), c(0xF2E5BC), c(0x3C3836), c(0xB57614), c(0xD5C4A1), c(0x282828),
				new SyntaxPalette(c(0x928374), c(0xAF3A03), c(0x9D0006), c(0x8F3F71), c(0xB57614), c(0x79740E),
						c(0x79740E), c(0x8F3F71), c(0x8F3F71), c(0x3C3836), c(0x3C3836), c(0x3C3836), c(0x076678),
						c(0x427B58), c(0xAF3A03), c(0x076678), c(0x9D0006), c(0x9D0006), c(0xB57614), c(0x79740E),
						c(0x076678), c(0xAF3A03), c(0x076678)));
	}

	private static Color c(int rgb) {
		return new Color(rgb);
	}

	/**
	 * Returns a softened tree-row selection that lets the surrounding theme surface show
	 * through while keeping the selected row distinct.
	 * @return tree selection color blended with the editor background
	 */
	public Color treeSelectionBackground() {
		return ThemeColors.blend(selectionBackground, background, 0.38f);
	}

}
