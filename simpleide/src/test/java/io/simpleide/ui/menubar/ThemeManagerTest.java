package io.simpleide.ui.menubar;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import io.simpleide.frame.log.LogPanel;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.menubar.menus.IdeMenuBar;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorWelcomeScreen;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.terminal.TerminalPanel;
import io.simpleide.ui.terminal.TerminalSettingsProvider;
import io.simpleide.ui.treefile.Tree;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JSplitPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link io.simpleide.ui.menubar.ThemeManager}.
 */
class ThemeManagerTest {

	private final ThemeManager themeManager = ThemeManager.getInstance();

	@AfterEach
	@SuppressWarnings("unused")
	void resetTheme() throws Exception {
		themeManager.setTheme(ThemeManager.ThemeType.DARK);
		flushEdt();
	}

	@Test
	void setThemeShouldApplyCorrespondingLookAndFeel() throws Exception {
		assertTheme(ThemeManager.ThemeType.LIGHT, FlatLightLaf.class);
		assertTheme(ThemeManager.ThemeType.DRACULA, FlatPropertiesLaf.class);
		assertTheme(ThemeManager.ThemeType.BROWN, FlatPropertiesLaf.class);
		assertTheme(ThemeManager.ThemeType.BROWN_LIGHT, FlatPropertiesLaf.class);
		assertTheme(ThemeManager.ThemeType.DARK, FlatDarkLaf.class);
	}

	@Test
	void setThemeShouldApplyCorrespondingEditorTheme() throws Exception {
		Editor editor = new Editor();
		themeManager.register(editor);

		assertEditorTheme(editor, ThemeManager.ThemeType.LIGHT, Color.WHITE);
		assertEditorTheme(editor, ThemeManager.ThemeType.DARK, new Color(0x293134));
		assertEditorTheme(editor, ThemeManager.ThemeType.DRACULA, new Color(0x282A36));
		assertEditorTheme(editor, ThemeManager.ThemeType.BROWN, new Color(0x282828));
		assertEditorTheme(editor, ThemeManager.ThemeType.BROWN_LIGHT, new Color(0xFBF1C7));
	}

	@Test
	void everyThemeShouldKeepUiAndEverySyntaxTokenReadable() throws Exception {
		Editor editor = new Editor();
		themeManager.register(editor);
		String[][] foregroundBackgroundPairs = { { "Label.foreground", "Panel.background" },
				{ "Tree.foreground", "Tree.background" }, { "MenuBar.foreground", "MenuBar.background" },
				{ "Menu.foreground", "Menu.background" }, { "MenuItem.foreground", "MenuItem.background" },
				{ "MenuItem.selectionForeground", "MenuItem.selectionBackground" },
				{ "Button.foreground", "Button.background" }, { "TextArea.foreground", "TextArea.background" },
				{ "TextField.foreground", "TextField.background" }, { "PopupMenu.foreground", "PopupMenu.background" },
				{ "ToolTip.foreground", "ToolTip.background" }, { "Table.foreground", "Table.background" },
				{ "List.foreground", "List.background" }, { "TabbedPane.foreground", "TabbedPane.background" },
				{ "TextPane.foreground", "TextPane.background" }, { "EditorPane.foreground", "EditorPane.background" },
				{ "FormattedTextField.foreground", "FormattedTextField.background" },
				{ "PasswordField.foreground", "PasswordField.background" },
				{ "ToggleButton.foreground", "ToggleButton.background" },
				{ "CheckBox.foreground", "CheckBox.background" },
				{ "RadioButton.foreground", "RadioButton.background" },
				{ "ComboBox.foreground", "ComboBox.background" }, { "Spinner.foreground", "Spinner.background" },
				{ "TableHeader.foreground", "TableHeader.background" },
				{ "OptionPane.messageForeground", "OptionPane.background" },
				{ "OptionPane.buttonForeground", "OptionPane.buttonBackground" },
				{ "FileChooser.foreground", "FileChooser.background" } };
		String[][] selectionPairs = { { "TextField.selectionForeground", "TextField.selectionBackground" },
				{ "TextArea.selectionForeground", "TextArea.selectionBackground" },
				{ "List.selectionForeground", "List.selectionBackground" },
				{ "Table.selectionForeground", "Table.selectionBackground" },
				{ "Tree.selectionForeground", "Tree.selectionBackground" },
				{ "ComboBox.selectionForeground", "ComboBox.selectionBackground" },
				{ "TabbedPane.selectedForeground", "TabbedPane.selectedBackground" },
				{ "MenuBar.selectionForeground", "MenuBar.selectionBackground" } };
		String[][] disabledPairs = { { "Label.disabledForeground", "Panel.background" },
				{ "Button.disabledForeground", "Button.disabledBackground" },
				{ "ToggleButton.disabledForeground", "ToggleButton.disabledBackground" },
				{ "TextField.inactiveForeground", "TextField.inactiveBackground" },
				{ "ComboBox.disabledForeground", "ComboBox.background" },
				{ "TabbedPane.disabledForeground", "TabbedPane.background" },
				{ "Menu.disabledForeground", "Menu.background" },
				{ "MenuItem.disabledForeground", "MenuItem.background" } };
		for (ThemeManager.ThemeType theme : ThemeManager.ThemeType.values()) {
			themeManager.setTheme(theme);
			flushEdt();
			for (String[] pair : foregroundBackgroundPairs) {
				Color foreground = UIManager.getColor(pair[0]);
				Color background = UIManager.getColor(pair[1]);
				assertThat(foreground).as("%s foreground for %s", pair[0], theme).isNotNull();
				assertThat(background).as("%s background for %s", pair[1], theme).isNotNull();
				assertThat(ThemeColors.contrastRatio(foreground, background))
					.as("contrast for %s on %s in %s", pair[0], pair[1], theme)
					.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			}
			for (String[] pair : selectionPairs) {
				Color foreground = UIManager.getColor(pair[0]);
				Color background = UIManager.getColor(pair[1]);
				assertThat(foreground).as("%s selection foreground for %s", pair[0], theme).isNotNull();
				assertThat(background).as("%s selection background for %s", pair[1], theme).isNotNull();
				assertThat(ThemeColors.contrastRatio(foreground, background))
					.as("selection contrast for %s on %s in %s", pair[0], pair[1], theme)
					.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			}
			for (String[] pair : disabledPairs) {
				Color foreground = UIManager.getColor(pair[0]);
				Color background = UIManager.getColor(pair[1]);
				assertThat(foreground).as("%s disabled foreground for %s", pair[0], theme).isNotNull();
				assertThat(background).as("%s disabled background for %s", pair[1], theme).isNotNull();
				assertThat(ThemeColors.contrastRatio(foreground, background))
					.as("disabled contrast for %s on %s in %s", pair[0], pair[1], theme)
					.isGreaterThanOrEqualTo(ThemeColors.MIN_LARGE_TEXT_CONTRAST);
			}
			Color editorBackground = editor.getBackground();
			for (int token = 0; token < editor.getSyntaxScheme().getStyleCount(); token++) {
				Color foreground = editor.getSyntaxScheme().getStyle(token).foreground;
				if (foreground != null) {
					assertThat(ThemeColors.contrastRatio(foreground, editorBackground))
						.as("syntax token %s contrast in %s", token, theme)
						.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
				}
			}
		}
	}

	@Test
	void allApplicationSurfacesShouldMatchTheirStaticThemePalette() throws Exception {
		for (ThemeManager.ThemeType theme : ThemeManager.ThemeType.values()) {
			PaletteFixture expected = palette(theme);
			themeManager.setTheme(theme);
			flushEdt();

			assertColor("Panel.background", expected.background(), theme);
			assertColor("Panel.foreground", expected.foreground(), theme);
			assertColor("Tree.background", expected.background(), theme);
			assertColor("Tree.foreground", expected.foreground(), theme);
			assertColor("Tree.textBackground", expected.background(), theme);
			assertColor("Tree.textForeground", expected.foreground(), theme);
			assertColor("MenuBar.background", expected.background(), theme);
			assertColor("MenuBar.foreground", expected.foreground(), theme);
			assertColor("MenuBar.selectionBackground", expected.selection(), theme);
			assertColor("MenuBar.selectionForeground", expected.selectionForeground(), theme);
			assertColor("TextArea.background", expected.background(), theme);
			assertColor("TextArea.foreground", expected.foreground(), theme);
			assertColor("TextField.background", expected.inputBackground(), theme);
			assertColor("TextField.caretForeground", expected.foreground(), theme);
			assertColor("Button.background", expected.buttonBackground(), theme);
			assertColor("PopupMenu.background", expected.popupBackground(), theme);
			assertColor("MenuBar.borderColor", expected.border(), theme);
			assertColor("SplitPaneDivider.background", expected.border(), theme);
			assertColor("TextPane.background", expected.background(), theme);
			assertColor("TextPane.foreground", expected.foreground(), theme);
			assertColor("EditorPane.background", expected.background(), theme);
			assertColor("EditorPane.foreground", expected.foreground(), theme);
			assertColor("Tree.selectionBackground", expected.selection(), theme);
			assertColor("Tree.selectionForeground", expected.selectionForeground(), theme);
			assertColor("MenuItem.selectionBackground", expected.selection(), theme);
			assertColor("MenuItem.selectionForeground", expected.selectionForeground(), theme);
			assertColor("SplitPane.background", expected.background(), theme);
			assertThat(UIManager.getColor("SplitPaneDivider.background")).as("split divider in %s", theme)
				.isNotNull()
				.isNotEqualTo(expected.background());
			assertThat(
					ThemeColors.contrastRatio(UIManager.getColor("SplitPaneDivider.gripColor"), expected.background()))
				.as("split grip in %s", theme)
				.isGreaterThanOrEqualTo(1.5d);
			assertThat(ThemeColors.contrastRatio(UIManager.getColor("MenuBar.foreground"),
					UIManager.getColor("MenuBar.background")))
				.as("menubar text in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);

			LogPanel console = new LogPanel();
			Tree tree = new Tree(new Directory(Path.of(".")));
			TerminalPanel terminal = new TerminalPanel();
			IdeMenuBar menuBar = new IdeMenuBar(() -> {
			}, () -> {
			}, () -> {
			}, () -> {
			});
			JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tree, terminal);
			JPanel welcome = EditorWelcomeScreen.create(() -> {
			}, () -> {
			});
			SwingUtilities.updateComponentTreeUI(console);
			SwingUtilities.updateComponentTreeUI(tree);
			SwingUtilities.updateComponentTreeUI(terminal);
			SwingUtilities.updateComponentTreeUI(menuBar);
			SwingUtilities.updateComponentTreeUI(splitPane);
			SwingUtilities.updateComponentTreeUI(welcome);
			assertThat(console.getBackground()).as("debug console background in %s", theme)
				.isEqualTo(expected.background());
			assertThat(console.getForeground()).as("debug console text in %s", theme).isEqualTo(expected.foreground());
			assertThat(tree.getBackground()).as("file tree background in %s", theme).isEqualTo(expected.background());
			assertThat(tree.getForeground()).as("file tree text in %s", theme).isEqualTo(expected.foreground());
			JLabel selectedTreeCell = (JLabel) tree.getCellRenderer()
				.getTreeCellRendererComponent(tree, tree.getModel().getRoot(), true, false, false, 0, false);
			assertThat(selectedTreeCell.getBackground()).as("selected file tree background in %s", theme)
				.isEqualTo(expected.selection());
			assertThat(ThemeColors.contrastRatio(selectedTreeCell.getForeground(), selectedTreeCell.getBackground()))
				.as("selected file tree text in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			assertThat(menuBar.getBackground()).as("menubar component background in %s", theme)
				.isEqualTo(expected.background());
			assertThat(menuBar.getForeground()).as("menubar component text in %s", theme)
				.isEqualTo(expected.foreground());
			assertThat(splitPane.getBackground()).as("split pane component background in %s", theme)
				.isEqualTo(expected.background());
			assertThat(welcome.getBackground()).as("welcome surface background in %s", theme)
				.isEqualTo(expected.background());
			List<JButton> welcomeButtons = new ArrayList<>();
			collectButtons(welcome, welcomeButtons);
			assertThat(welcomeButtons).hasSize(2);
			for (JButton button : welcomeButtons) {
				assertThat(ThemeColors.contrastRatio(button.getForeground(), button.getBackground()))
					.as("welcome button text contrast in %s", theme)
					.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			}
			assertThat(new TerminalSettingsProvider().getDefaultForeground().toColor().getRGB())
				.as("terminal text in %s", theme)
				.isEqualTo(expected.foreground().getRGB());
			assertThat(new TerminalSettingsProvider().getDefaultBackground().toColor().getRGB())
				.as("terminal background in %s", theme)
				.isEqualTo(expected.background().getRGB());
			assertThat(terminal.getBackground()).as("terminal controls background in %s", theme)
				.isEqualTo(expected.background());
			List<JButton> terminalActions = new ArrayList<>();
			collectButtons(terminal, terminalActions);
			assertThat(terminalActions).extracting(JButton::getToolTipText)
				.containsExactlyInAnyOrder("Add terminal", "Close selected terminal");
			for (JButton button : terminalActions) {
				assertThat(button.getBackground()).as("terminal action button background in %s", theme)
					.isEqualTo(UIManager.getColor("Button.background"));
				assertThat(ThemeColors.contrastRatio(button.getForeground(), button.getBackground()))
					.as("terminal action icon contrast in %s", theme)
					.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
				assertThat(button.getIcon().getIconWidth()).as("terminal action icon size in %s", theme)
					.isEqualTo(Math.max(12, UIManager.getFont("Menu.font").getSize()));
			}
		}
	}

	private void collectButtons(Container container, List<JButton> buttons) {
		for (Component component : container.getComponents()) {
			if (component instanceof JButton button) {
				buttons.add(button);
			}
			if (component instanceof Container child) {
				collectButtons(child, buttons);
			}
		}
	}

	private PaletteFixture palette(ThemeManager.ThemeType theme) {
		return switch (theme) {
			case DARK -> new PaletteFixture(new Color(0x3C3F41), new Color(0xBBBBBB), new Color(0x4B6EAF), Color.WHITE,
					new Color(0x2B2B2B), new Color(0x4B6EAF), new Color(0x2B2B2B), new Color(0x55595B));
			case LIGHT -> new PaletteFixture(new Color(0xF2F2F2), new Color(0x1F1F1F), new Color(0x2675BF), Color.WHITE,
					Color.WHITE, new Color(0x2675BF), Color.WHITE, new Color(0xD0D0D0));
			case DRACULA ->
				new PaletteFixture(new Color(0x282A36), new Color(0xF8F8F2), new Color(0x44475A), new Color(0xF8F8F2),
						new Color(0x282A36), new Color(0x44475A), new Color(0x21222C), new Color(0x44475A));
			case DRACULA_LIGHT -> new PaletteFixture(new Color(0xF8F8F2), new Color(0x282A36), new Color(0xE6E6DC),
					new Color(0x282A36), Color.WHITE, new Color(0xE6E6DC), Color.WHITE, new Color(0xD6D6CC));
			case BROWN ->
				new PaletteFixture(new Color(0x282828), new Color(0xEBDBB2), new Color(0x504945), new Color(0xFBF1C7),
						new Color(0x3C3836), new Color(0x3C3836), new Color(0x282828), new Color(0x504945));
			case BROWN_LIGHT ->
				new PaletteFixture(new Color(0xFBF1C7), new Color(0x3C3836), new Color(0xD5C4A1), new Color(0x282828),
						new Color(0xF2E5BC), new Color(0xEBDBB2), new Color(0xFBF1C7), new Color(0xD5C4A1));
		};
	}

	private void assertColor(String key, Color expected, ThemeManager.ThemeType theme) {
		assertThat(UIManager.getColor(key)).as("%s in %s", key, theme).isEqualTo(expected);
	}

	private record PaletteFixture(Color background, Color foreground, Color selection, Color selectionForeground,
			Color inputBackground, Color buttonBackground, Color popupBackground, Color border) {
	}

	@Test
	void draculaShouldUseVisualStudioCodePaletteAcrossEditorAndUi() throws Exception {
		Editor editor = new Editor();
		themeManager.register(editor);
		themeManager.setTheme(ThemeManager.ThemeType.DRACULA);
		flushEdt();

		assertThat(editor.getBackground()).isEqualTo(new Color(0x282A36));
		assertThat(ThemeColors.contrastRatio(editor.getSyntaxScheme().getStyle(TokenTypes.COMMENT_EOL).foreground,
				editor.getBackground()))
			.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
			.isEqualTo(new Color(0xFF79C6));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.DATA_TYPE).foreground).isEqualTo(new Color(0x8BE9FD));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.IDENTIFIER).foreground).isEqualTo(new Color(0x8BE9FD));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.FUNCTION).foreground).isEqualTo(new Color(0x50FA7B));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD_2).foreground)
			.isEqualTo(new Color(0xBD93F9));
		assertThat(ThemeColors.contrastRatio(editor.getSyntaxScheme().getStyle(TokenTypes.COMMENT_MULTILINE).foreground,
				editor.getBackground()))
			.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_FLOAT).foreground)
			.isNotEqualTo(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).foreground);
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.MARKUP_TAG_NAME).foreground)
			.isEqualTo(new Color(0xFF6B9E));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.ERROR_CHAR).foreground).isEqualTo(new Color(0xFF9B9B));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.VARIABLE).foreground).isEqualTo(new Color(0xFFB86C));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground)
			.isEqualTo(new Color(0xF1FA8C));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).foreground)
			.isEqualTo(new Color(0xBD93F9));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.SEPARATOR).foreground).isEqualTo(new Color(0xFF79C6));
		assertThat(editor.getMatchedBracketBGColor()).isEqualTo(new Color(0x44475A));
		assertThat(editor.getMatchedBracketBorderColor()).isEqualTo(new Color(0xFF79C6));
		assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x282A36));
		assertThat(UIManager.getColor("MenuBar.hoverBackground")).isEqualTo(new Color(0x44475A));
		assertThat(UIManager.getColor("Button.background")).isEqualTo(new Color(0x44475A));
	}

	@Test
	void brownThemesShouldUseDistinctReadableSyntaxColors() throws Exception {
		Editor editor = new Editor();
		themeManager.register(editor);
		themeManager.setTheme(ThemeManager.ThemeType.BROWN);
		flushEdt();

		assertThat(ThemeColors.contrastRatio(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground,
				editor.getBackground()))
			.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.FUNCTION).foreground).isEqualTo(new Color(0xB8BB26));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.IDENTIFIER).foreground).isEqualTo(new Color(0x83A598));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.ANNOTATION).foreground).isEqualTo(new Color(0xFE8019));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground)
			.isEqualTo(new Color(0xB8BB26));
		assertThat(UIManager.getColor("Tree.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("MenuBar.hoverBackground")).isEqualTo(new Color(0x504945));
		assertThat(UIManager.getColor("TextPane.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("MenuBar.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("PopupMenu.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("OptionPane.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("Button.background")).isEqualTo(new Color(0x3C3836));
		LogPanel logPanel = new LogPanel();
		SwingUtilities.updateComponentTreeUI(logPanel);
		assertThat(logPanel.getBackground()).isEqualTo(new Color(0x282828));

		themeManager.setTheme(ThemeManager.ThemeType.BROWN_LIGHT);
		flushEdt();
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
			.isEqualTo(new Color(0x9D0006));
		assertThat(UIManager.getColor("Tree.background")).isEqualTo(new Color(0xFBF1C7));
		assertThat(UIManager.getColor("MenuBar.hoverBackground")).isEqualTo(new Color(0xD5C4A1));
		assertThat(UIManager.getColor("TextPane.background")).isEqualTo(new Color(0xFBF1C7));
		assertThat(UIManager.getColor("MenuBar.background")).isEqualTo(new Color(0xFBF1C7));
		assertThat(UIManager.getColor("PopupMenu.background")).isEqualTo(new Color(0xFBF1C7));
		assertThat(UIManager.getColor("OptionPane.background")).isEqualTo(new Color(0xFBF1C7));
		assertThat(UIManager.getColor("Button.background")).isEqualTo(new Color(0xEBDBB2));
	}

	private void assertTheme(ThemeManager.ThemeType theme, Class<?> expectedLookAndFeel) throws Exception {
		themeManager.setTheme(theme);
		flushEdt();

		assertThat(UIManager.getLookAndFeel().getClass()).isEqualTo(expectedLookAndFeel);
	}

	private void assertEditorTheme(Editor editor, ThemeManager.ThemeType theme, Color expectedBackground)
			throws Exception {
		themeManager.setTheme(theme);
		flushEdt();

		assertThat(editor.getBackground()).isEqualTo(expectedBackground);
	}

	private void flushEdt() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
		});
	}

}
