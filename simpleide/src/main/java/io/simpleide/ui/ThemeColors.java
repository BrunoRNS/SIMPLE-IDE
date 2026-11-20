package io.simpleide.ui;

import java.awt.Color;

import javax.swing.UIManager;

/**
 * Colour helpers that keep custom-painted and custom-rendered components readable.
 *
 * <p>
 * The helpers expose WCAG contrast calculations, a few theme lookups and a contrast
 * adjuster used by the file tree, terminal, log console and icon loaders. Colours are
 * never replaced when they already satisfy the requested contrast ratio, so themes keep
 * their identity.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ThemeColors {

	public static final double MIN_TEXT_CONTRAST = 4.5d;

	public static final double MIN_LARGE_TEXT_CONTRAST = 3.0d;

	private ThemeColors() {
	}

	/**
	 * Looks up a UI colour and falls back to another key when the first one is absent.
	 * @param keys UI default keys in priority order
	 * @return the first available colour, or {@code null}
	 */
	public static Color ui(String... keys) {
		for (String key : keys) {
			if (key != null) {
				Color color = UIManager.getColor(key);
				if (color != null) {
					return color;
				}
			}
		}
		return null;
	}

	/**
	 * Returns whether a colour is visually light.
	 * @param color colour to inspect
	 * @return {@code true} when the colour luminance is above the midpoint
	 */
	public static boolean isLight(Color color) {
		return color != null && relativeLuminance(color) > 0.5d;
	}

	/**
	 * Returns a foreground that is readable on the supplied background.
	 * @param foreground preferred foreground, may be {@code null}
	 * @param background background colour, may be {@code null}
	 * @return a colour with at least {@link #MIN_TEXT_CONTRAST} contrast
	 */
	public static Color readable(Color foreground, Color background) {
		return readable(foreground, background, MIN_TEXT_CONTRAST);
	}

	/**
	 * Returns a foreground that satisfies a minimum contrast ratio.
	 * @param foreground preferred foreground, may be {@code null}
	 * @param background background colour, may be {@code null}
	 * @param minimumContrast minimum WCAG contrast ratio
	 * @return a readable colour
	 */
	public static Color readable(Color foreground, Color background, double minimumContrast) {
		if (background == null) {
			return foreground == null ? Color.WHITE : foreground;
		}
		if (foreground != null && contrastRatio(foreground, background) >= minimumContrast) {
			return foreground;
		}
		Color black = contrastRatio(Color.BLACK, background) >= contrastRatio(Color.WHITE, background) ? Color.BLACK
				: Color.WHITE;
		if (contrastRatio(black, background) >= minimumContrast) {
			return black;
		}
		return contrastRatio(Color.WHITE, background) >= contrastRatio(Color.BLACK, background) ? Color.WHITE
				: Color.BLACK;
	}

	/**
	 * Mixes a foreground into a background.
	 * @param foreground foreground colour
	 * @param background background colour
	 * @param foregroundRatio amount of foreground between {@code 0} and {@code 1}
	 * @return the blended colour
	 */
	public static Color blend(Color foreground, Color background, float foregroundRatio) {
		if (foreground == null) {
			return background;
		}
		if (background == null) {
			return foreground;
		}
		float backgroundRatio = 1f - foregroundRatio;
		int red = Math.round(foreground.getRed() * foregroundRatio + background.getRed() * backgroundRatio);
		int green = Math.round(foreground.getGreen() * foregroundRatio + background.getGreen() * backgroundRatio);
		int blue = Math.round(foreground.getBlue() * foregroundRatio + background.getBlue() * backgroundRatio);
		return new Color(clamp(red), clamp(green), clamp(blue));
	}

	/**
	 * Calculates the WCAG contrast ratio between two colours.
	 * @param first first colour
	 * @param second second colour
	 * @return the contrast ratio, or {@code 21} when one colour is missing
	 */
	public static double contrastRatio(Color first, Color second) {
		if (first == null || second == null) {
			return 21.0d;
		}
		double firstLuminance = relativeLuminance(first);
		double secondLuminance = relativeLuminance(second);
		double lighter = Math.max(firstLuminance, secondLuminance);
		double darker = Math.min(firstLuminance, secondLuminance);
		return (lighter + 0.05d) / (darker + 0.05d);
	}

	/**
	 * Returns the relative luminance of a colour.
	 * @param color colour to inspect
	 * @return the luminance between {@code 0} and {@code 1}
	 */
	public static double relativeLuminance(Color color) {
		if (color == null) {
			return 0.0d;
		}
		return 0.2126d * linearize(color.getRed()) + 0.7152d * linearize(color.getGreen())
				+ 0.0722d * linearize(color.getBlue());
	}

	private static double linearize(int channel) {
		double normalized = channel / 255.0d;
		return normalized <= 0.04045d ? normalized / 12.92d : Math.pow((normalized + 0.055d) / 1.055d, 2.4d);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(255, value));
	}

}
