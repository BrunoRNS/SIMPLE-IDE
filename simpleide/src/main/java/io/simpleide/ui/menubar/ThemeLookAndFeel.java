package io.simpleide.ui.menubar;

import java.io.IOException;
import java.io.InputStream;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.formdev.flatlaf.FlatPropertiesLaf;

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class ThemeLookAndFeel {

	private ThemeLookAndFeel() {
	}

	static void setup(String resourceName, String themeName) {
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

}
