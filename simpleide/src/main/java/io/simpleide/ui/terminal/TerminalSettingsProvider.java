package io.simpleide.ui.terminal;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.ColorPaletteImpl;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import io.simpleide.ui.ThemeColors;
import java.awt.Font;
import javax.swing.UIManager;

/**
 * JediTerm settings derived from the active Swing theme.
 *
 * <p>
 * The terminal uses a monospaced variant of the UI text font and the current
 * {@code TextArea} foreground and background colors, so it follows theme changes.
 *
 * <p>
 * Covered by {@code TerminalSettingsProviderTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class TerminalSettingsProvider extends DefaultSettingsProvider {

	/**
	 * Returns the monospaced terminal font, based on the UI text font when available.
	 * @return the terminal font
	 */
	@Override
	public Font getTerminalFont() {
		Font uiFont = UIManager.getFont("TextArea.font");
		if (uiFont == null) {
			uiFont = UIManager.getFont("TextPane.font");
		}
		if (uiFont == null) {
			uiFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
		}
		return new Font(Font.MONOSPACED, uiFont.getStyle(), uiFont.getSize());
	}

	/**
	 * Returns the terminal font size.
	 * @return the size of the terminal font
	 */
	@Override
	public float getTerminalFontSize() {
		return getTerminalFont().getSize2D();
	}

	/**
	 * Returns the theme foreground color for terminal text.
	 * @return the default foreground color
	 */
	@Override
	public TerminalColor getDefaultForeground() {
		return new TerminalColor(() -> getThemeColor("TextArea.foreground", java.awt.Color.WHITE));
	}

	/**
	 * Returns the theme background color for the terminal.
	 * @return the default background color
	 */
	@Override
	public TerminalColor getDefaultBackground() {
		return new TerminalColor(() -> getThemeColor("TextArea.background", java.awt.Color.BLACK));
	}

	/**
	 * Returns the default terminal text style.
	 * @return the foreground and background style
	 */
	@Override
	public TextStyle getDefaultStyle() {
		return new TextStyle(getDefaultForeground(), getDefaultBackground());
	}

	/**
	 * Returns a palette that keeps ANSI colours readable against the current theme
	 * background.
	 * @return the contrast-aware terminal palette
	 */
	@Override
	public ColorPalette getTerminalColorPalette() {
		return new ContrastPalette();
	}

	/**
	 * Returns a selection style derived from the active theme selection colours.
	 * @return the selection text style
	 */
	@Override
	public TextStyle getSelectionColor() {
		java.awt.Color foreground = ThemeColors.ui("TextArea.selectionForeground", "TextField.selectionForeground");
		java.awt.Color background = ThemeColors.ui("TextArea.selectionBackground", "TextField.selectionBackground");
		if (foreground == null || background == null) {
			return super.getSelectionColor();
		}
		java.awt.Color readable = ThemeColors.readable(foreground, background, ThemeColors.MIN_TEXT_CONTRAST);
		return new TextStyle(TerminalColor.color(toTerminalColor(readable)),
				TerminalColor.color(toTerminalColor(background)));
	}

	private static Color toTerminalColor(java.awt.Color color) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private static final class ContrastPalette extends ColorPalette {

		@Override
		protected Color getForegroundByColorIndex(int index) {
			Color base = ColorPaletteImpl.XTERM_PALETTE.getForeground(TerminalColor.index(index));
			java.awt.Color background = UIManager.getColor("TextArea.background");
			if (background == null) {
				background = UIManager.getColor("TextPane.background");
			}
			java.awt.Color foreground = new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue(),
					base.getAlpha());
			java.awt.Color readable = ThemeColors.readable(foreground, background, ThemeColors.MIN_LARGE_TEXT_CONTRAST);
			return new Color(readable.getRed(), readable.getGreen(), readable.getBlue(), readable.getAlpha());
		}

		@Override
		protected Color getBackgroundByColorIndex(int index) {
			return ColorPaletteImpl.XTERM_PALETTE.getBackground(TerminalColor.index(index));
		}

	}

	private static Color getThemeColor(String key, java.awt.Color fallback) {
		java.awt.Color color = UIManager.getColor(key);
		if (color == null) {
			color = fallback;
		}
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

}
