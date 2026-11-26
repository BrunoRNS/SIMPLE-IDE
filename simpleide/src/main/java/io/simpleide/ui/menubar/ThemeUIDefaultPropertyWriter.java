package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Insets;

import javax.swing.UIDefaults;
import javax.swing.UIManager;

import io.simpleide.ui.ThemePalette;

/** Writes related component colors and margins into Swing defaults. */
/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class ThemeUIDefaultPropertyWriter {

	private ThemeUIDefaultPropertyWriter() {
	}

	static void putTextLike(UIDefaults d, String prefix, Color background, Color foreground, Color selectionBackground,
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

	static void putMenuLike(UIDefaults d, String prefix, ThemePalette p) {
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

	static void putCheckBoxLike(UIDefaults d, String prefix, ThemePalette p) {
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

	static void put(UIDefaults d, String key, Object value) {
		if (value != null) {
			d.put(key, value);
			UIManager.put(key, value);
		}
	}

	static Color alpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

}
