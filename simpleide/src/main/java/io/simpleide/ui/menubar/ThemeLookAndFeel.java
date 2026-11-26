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
