package io.simpleide.ui.terminal;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
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

	private static Color getThemeColor(String key, java.awt.Color fallback) {
		java.awt.Color color = UIManager.getColor(key);
		if (color == null) {
			color = fallback;
		}
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

}
