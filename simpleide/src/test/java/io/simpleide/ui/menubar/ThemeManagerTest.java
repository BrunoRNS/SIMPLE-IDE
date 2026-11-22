package io.simpleide.ui.menubar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import static org.assertj.core.api.Assertions.assertThat;
import org.fife.ui.rsyntaxtextarea.TokenTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;

import io.simpleide.frame.log.LogPanel;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.editor.Editor;
import io.simpleide.ui.editor.EditorWelcomeScreen;
import io.simpleide.ui.menubar.menus.IdeMenuBar;
import io.simpleide.ui.menubar.menus.SettingsMenu;
import io.simpleide.ui.terminal.TerminalPanel;
import io.simpleide.ui.terminal.TerminalSettingsProvider;
import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.Tree;

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
		assertEditorTheme(editor, ThemeManager.ThemeType.DARK,
				ThemePalette.of(ThemeManager.ThemeType.DARK).background());
		assertEditorTheme(editor, ThemeManager.ThemeType.DRACULA, new Color(0x282A36));
		assertEditorTheme(editor, ThemeManager.ThemeType.BROWN, new Color(0x282828));
		assertEditorTheme(editor, ThemeManager.ThemeType.DRACULA_LIGHT,
				ThemePalette.of(ThemeManager.ThemeType.DRACULA_LIGHT).background());
		assertEditorTheme(editor, ThemeManager.ThemeType.BROWN_LIGHT,
				ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).background());
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
						.as("syntax token %s contrast in %s (foreground=%s, background=%s)", token, theme, foreground,
								editorBackground)
						.isGreaterThanOrEqualTo(3.0d);
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
			assertColor("MenuBar.background", ThemePalette.of(theme).menuBarBackground(), theme);
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
			assertColor("Component.borderColor", expected.border(), theme);
			assertColor("Component.focusedBorderColor", ThemePalette.of(theme).focusBorder(), theme);
			assertColor("Button.borderColor", expected.border(), theme);
			assertColor("TextField.borderColor", expected.border(), theme);
			assertColor("PopupMenu.borderColor", expected.border(), theme);
			assertColor("ToolTip.borderColor", expected.border(), theme);
			assertColor("Tree.selectionBorderColor", ThemePalette.of(theme).treeSelectionBackground(), theme);
			assertColor("TabbedPane.underlineColor", ThemePalette.of(theme).tabUnderline(), theme);
			assertColor("SplitPaneDivider.background", expected.background(), theme);
			assertColor("TextPane.background", expected.background(), theme);
			assertColor("TextPane.foreground", expected.foreground(), theme);
			assertColor("EditorPane.background", expected.background(), theme);
			assertColor("EditorPane.foreground", expected.foreground(), theme);
			assertColor("Tree.selectionBackground", ThemePalette.of(theme).treeSelectionBackground(), theme);
			assertColor("Tree.selectionForeground", expected.selectionForeground(), theme);
			assertColor("MenuItem.selectionBackground", ThemePalette.of(theme).menuSelectionBackground(), theme);
			assertColor("MenuItem.selectionForeground", ThemePalette.of(theme).menuSelectionForeground(), theme);
			assertColor("SplitPane.background", expected.background(), theme);
			assertThat(UIManager.getInt("SplitPane.dividerSize")).as("split drag target in %s", theme)
				.isGreaterThanOrEqualTo(8);
			assertThat(UIManager.getInt("Component.arc")).as("modern component corner radius in %s", theme)
				.isGreaterThanOrEqualTo(10);
			assertThat(UIManager.getInt("Button.arc")).as("rounded button corner radius in %s", theme)
				.isGreaterThanOrEqualTo(10);
			assertThat(UIManager.getBoolean("Tree.wideSelection")).as("full width tree selection in %s", theme)
				.isTrue();
			assertThat(UIManager.get("Tree.selectionInsets")).isInstanceOf(Insets.class);
			Insets treeSelectionInsets = (Insets) UIManager.get("Tree.selectionInsets");
			assertThat(treeSelectionInsets.top).as("tree row top inset in %s", theme).isEqualTo(2);
			assertThat(treeSelectionInsets.bottom).as("tree row bottom inset in %s", theme).isEqualTo(2);
			assertColor("SplitPaneDivider.background", expected.background(), theme);
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
			themeManager.refreshComponentTheme(menuBar, ThemePalette.of(theme));
			SwingUtilities.updateComponentTreeUI(splitPane);
			SwingUtilities.updateComponentTreeUI(welcome);
			assertThat(console.getBackground()).as("debug console background in %s", theme)
				.isEqualTo(expected.background());
			assertThat(console.getForeground()).as("debug console text in %s", theme).isEqualTo(expected.foreground());
			assertThat(tree.getBackground()).as("file tree background in %s", theme).isEqualTo(expected.background());
			assertThat(tree.getForeground()).as("file tree text in %s", theme).isEqualTo(expected.foreground());
			DefaultTreeCellRenderer selectedTreeCell = (DefaultTreeCellRenderer) tree.getCellRenderer()
				.getTreeCellRendererComponent(tree, tree.getModel().getRoot(), true, false, false, 0, false);
			assertThat(selectedTreeCell.getBackgroundSelectionColor()).as("selected file tree background in %s", theme)
				.isEqualTo(ThemePalette.of(theme).treeSelectionBackground());
			assertThat(selectedTreeCell.getFont()).as("selected file tree font in %s", theme).isEqualTo(tree.getFont());
			assertThat(selectedTreeCell.getTextSelectionColor()).as("selected file tree text color in %s", theme)
				.isEqualTo(ThemePalette.of(theme).selectionForeground());
			assertThat(selectedTreeCell.getBorderSelectionColor()).as("selection outline blends into fill in %s", theme)
				.isEqualTo(ThemePalette.of(theme).treeSelectionBackground());
			DefaultTreeCellRenderer normalTreeCell = (DefaultTreeCellRenderer) tree.getCellRenderer()
				.getTreeCellRendererComponent(tree, tree.getModel().getRoot(), false, false, false, 0, false);
			assertThat(normalTreeCell.getFont()).as("normal file tree font in %s", theme).isEqualTo(tree.getFont());
			assertThat(normalTreeCell.getBackgroundNonSelectionColor()).as("unselected file row color in %s", theme)
				.isEqualTo(ThemePalette.of(theme).background());
			assertThat(normalTreeCell.getTextNonSelectionColor()).as("unselected file row text in %s", theme)
				.isEqualTo(ThemePalette.of(theme).foreground());
			assertThat(ThemeColors.contrastRatio(selectedTreeCell.getForeground(), selectedTreeCell.getBackground()))
				.as("selected file tree text in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			assertThat(menuBar.getBackground()).as("menubar component background in %s", theme)
				.isEqualTo(ThemePalette.of(theme).menuBarBackground());
			assertThat(menuBar.getForeground()).as("menubar component text in %s", theme)
				.isEqualTo(expected.foreground());
			assertThat(menuBar.isOpaque()).as("menubar paints its themed surface in %s", theme).isTrue();
			for (Component menu : menuBar.getComponents()) {
				assertThat(menu).as("top level menu in %s", theme).isInstanceOf(JMenu.class);
				assertThat(menu.getBackground()).as("top level menu surface in %s", theme)
					.isEqualTo(ThemePalette.of(theme).menuBarBackground());
				assertThat(menu.isOpaque()).as("top level menu paints its themed surface in %s", theme).isTrue();
			}
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
			JLabel welcomeTitle = collectLabels(welcome).stream()
				.filter(label -> "Welcome to SIMPLE-IDE".equals(label.getText()))
				.findFirst()
				.orElseThrow();
			assertThat(welcomeTitle.getFont().getSize()).as("welcome title size in %s", theme)
				.isGreaterThanOrEqualTo(26);
			assertThat(ThemeColors.contrastRatio(welcomeTitle.getForeground(), welcome.getBackground()))
				.as("welcome title contrast in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			assertThat(ThemeColors.contrastRatio(welcomeTitle.getForeground(), ThemePalette.of(theme).surface()))
				.as("welcome title contrast over the theme surface in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			JLabel welcomeSubtitle = collectLabels(welcome).stream()
				.filter(label -> "Open a project or create a file to get started".equals(label.getText()))
				.findFirst()
				.orElseThrow();
			assertThat(welcomeSubtitle.getFont().getSize()).as("welcome subtitle size in %s", theme)
				.isGreaterThanOrEqualTo(15);
			assertThat(ThemeColors.contrastRatio(welcomeSubtitle.getForeground(), welcome.getBackground()))
				.as("welcome subtitle contrast in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			assertThat(ThemeColors.contrastRatio(welcomeSubtitle.getForeground(), ThemePalette.of(theme).surface()))
				.as("welcome subtitle contrast over the theme surface in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
			assertThat(welcomeButtons).allSatisfy(button -> assertThat(button.getClientProperty("JButton.buttonType"))
				.as("welcome button has rounded FlatLaf styling in %s", theme)
				.isEqualTo("roundRect"));
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
					.isEqualTo(Math.max(18, UIManager.getFont("Menu.font").getSize()));
				BufferedImage iconImage = new BufferedImage(button.getIcon().getIconWidth(),
						button.getIcon().getIconHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = iconImage.createGraphics();
				try {
					button.getIcon().paintIcon(button, graphics, 0, 0);
				}
				finally {
					graphics.dispose();
				}
				assertThat(hasVisiblePixel(iconImage)).as("rendered SVG icon %s", button.getToolTipText()).isTrue();
				assertThat(hasPixelColor(iconImage, button.getForeground()))
					.as("theme foreground SVG color %s in %s", button.getToolTipText(), theme)
					.isTrue();
			}
		}
	}

	@Test
	void nestedMenuItemsAdoptTheSelectedThemePalette() throws Exception {
		JMenuBar menuBar = new JMenuBar();
		JMenu settings = new JMenu("Settings");
		JMenu theme = new JMenu("Theme");
		JMenuItem light = new JMenuItem("Light");
		theme.add(light);
		settings.add(theme);
		menuBar.add(settings);

		for (ThemeManager.ThemeType type : ThemeManager.ThemeType.values()) {
			themeManager.setTheme(type);
			flushEdt();
			ThemePalette palette = ThemePalette.of(type);
			themeManager.refreshComponentTheme(menuBar, palette);
			assertThat(settings.getBackground()).as("top menu background in %s", type)
				.isEqualTo(palette.menuBarBackground());
			assertThat(theme.getBackground()).as("submenu background in %s", type).isEqualTo(palette.popupBackground());
			assertThat(theme.getForeground()).as("submenu foreground in %s", type).isEqualTo(palette.popupForeground());
			assertThat(light.getBackground()).as("nested item background in %s", type)
				.isEqualTo(palette.popupBackground());
			assertThat(light.getForeground()).as("nested item foreground in %s", type)
				.isEqualTo(palette.popupForeground());
		}
	}

	@Test
	void fontSizeSpinnerInputAdoptsEverySelectedTheme() throws Exception {
		JMenuBar menuBar = new JMenuBar();
		SettingsMenu settings = new SettingsMenu();
		menuBar.add(settings);
		JSpinner spinner = (JSpinner) settings.getMenuComponent(2);
		JFormattedTextField editor = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		ThemePalette darkPalette = ThemePalette.of(ThemeManager.ThemeType.DARK);

		for (ThemeManager.ThemeType theme : ThemeManager.ThemeType.values()) {
			themeManager.setTheme(theme);
			flushEdt();
			SwingUtilities.invokeAndWait(() -> SwingUtilities.updateComponentTreeUI(menuBar));

			spinner.setBackground(darkPalette.inputBackground());
			spinner.setForeground(darkPalette.inputForeground());
			editor.setBackground(darkPalette.inputBackground());
			editor.setForeground(darkPalette.inputForeground());
			editor.setCaretColor(darkPalette.inputForeground());

			ThemePalette palette = ThemePalette.of(theme);
			themeManager.refreshComponentTheme(menuBar, palette);

			assertThat(spinner.getBackground()).as("font size control background in %s", theme)
				.isEqualTo(palette.inputBackground());
			assertThat(spinner.getForeground()).as("font size control text in %s", theme)
				.isEqualTo(palette.inputForeground());
			assertThat(editor.getBackground()).as("font size editor background in %s", theme)
				.isEqualTo(palette.inputBackground());
			assertThat(editor.getForeground()).as("font size editor text in %s", theme)
				.isEqualTo(palette.inputForeground());
			assertThat(editor.getCaretColor()).as("font size caret in %s", theme).isEqualTo(palette.inputForeground());
			assertThat(ThemeColors.contrastRatio(editor.getForeground(), editor.getBackground()))
				.as("font size text contrast in %s", theme)
				.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
		}
	}

	@Test
	void lightPalettesMustKeepTheirOwnLightSurfacesAndSubtleBorders() throws Exception {
		assertLightPalette(ThemeManager.ThemeType.DRACULA_LIGHT, new Color(0xFAF8FF), new Color(0xF1EDF8),
				new Color(0x282433), new Color(0xE1DCEC), new Color(0x7C5CE7));
		assertLightPalette(ThemeManager.ThemeType.BROWN_LIGHT, new Color(0xF8F5EA), new Color(0xF1ECDC),
				new Color(0x3C3836), new Color(0xE4DAC2), new Color(0xB57614));
	}

	private void assertLightPalette(ThemeManager.ThemeType theme, Color background, Color surface, Color foreground,
			Color border, Color accent) throws Exception {
		themeManager.setTheme(theme);
		flushEdt();
		ThemePalette palette = ThemePalette.of(theme);
		assertThat(palette.background()).as("light theme background for %s", theme).isEqualTo(background);
		assertThat(palette.surface()).as("light theme surface for %s", theme).isEqualTo(surface);
		assertThat(palette.foreground()).as("light theme foreground for %s", theme).isEqualTo(foreground);
		assertThat(palette.border()).as("light theme border for %s", theme).isEqualTo(border);
		assertThat(palette.accent()).as("light theme accent for %s", theme).isEqualTo(accent);
		assertThat(ThemeColors.relativeLuminance(palette.background())).as("light canvas luminance for %s", theme)
			.isGreaterThan(0.85d);
		assertThat(ThemeColors.contrastRatio(palette.border(), palette.background()))
			.as("subtle border contrast for %s", theme)
			.isLessThan(1.5d);
		assertColor("Panel.background", background, theme);
		assertColor("TextPane.background", background, theme);
		assertColor("TabbedPane.background", surface, theme);
		assertColor("MenuBar.background", palette.menuBarBackground(), theme);
		assertColor("Button.background", palette.buttonBackground(), theme);
		assertColor("Button.foreground", palette.buttonForeground(), theme);
		assertColor("SplitPaneDivider.background", background, theme);
		assertThat(ThemeColors.contrastRatio(foreground, background)).as("body text contrast for %s", theme)
			.isGreaterThanOrEqualTo(ThemeColors.MIN_TEXT_CONTRAST);
	}

	@Test
	void everyLightThemeMustReplaceStaleDarkColorsOnRealNestedComponents() throws Exception {
		for (ThemeManager.ThemeType theme : List.of(ThemeManager.ThemeType.LIGHT, ThemeManager.ThemeType.DRACULA_LIGHT,
				ThemeManager.ThemeType.BROWN_LIGHT)) {
			themeManager.setTheme(theme);
			flushEdt();
			ThemePalette palette = ThemePalette.of(theme);
			JPanel root = new JPanel();
			JTabbedPane tabs = new JTabbedPane();
			JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JPanel(), new JPanel());
			JScrollPane scroll = new JScrollPane(new JPanel());
			JTree tree = new Tree(new Directory(Path.of(".")));
			LogPanel console = new LogPanel();
			TerminalPanel terminal = new TerminalPanel();
			root.add(tabs);
			root.add(split);
			root.add(scroll);
			root.add(tree);
			root.add(console);
			root.add(terminal);
			for (Component component : List.of(root, tabs, split, scroll, scroll.getViewport(), tree, console,
					terminal)) {
				component.setBackground(Color.BLACK);
				component.setForeground(Color.BLACK);
			}

			themeManager.refreshComponentTheme(root, palette);

			assertThat(root.getBackground()).as("root surface in %s", theme).isEqualTo(palette.background());
			assertThat(tabs.getBackground()).as("editor tabs in %s", theme).isEqualTo(palette.surface());
			assertThat(split.getBackground()).as("split surface in %s", theme).isEqualTo(palette.background());
			assertThat(split.getDividerSize()).as("drag area in %s", theme).isGreaterThanOrEqualTo(8);
			assertThat(scroll.getViewport().getBackground()).as("scroll viewport in %s", theme)
				.isEqualTo(palette.background());
			assertThat(tree.getBackground()).as("tree background in %s", theme).isEqualTo(palette.background());
			assertThat(console.getBackground()).as("console background in %s", theme).isEqualTo(palette.background());
			assertThat(terminal.getBackground()).as("terminal background in %s", theme).isEqualTo(palette.background());
			List<JButton> buttons = new ArrayList<>();
			collectButtons(terminal, buttons);
			assertThat(buttons).hasSize(2).allSatisfy(button -> {
				assertThat(button.getBackground()).isEqualTo(palette.buttonBackground());
				assertThat(button.getForeground()).isEqualTo(palette.buttonForeground());
			});
		}
	}

	private boolean hasVisiblePixel(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasPixelColor(BufferedImage image, Color expected) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (image.getRGB(x, y) == expected.getRGB()) {
					return true;
				}
			}
		}
		return false;
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

	private List<JLabel> collectLabels(Container container) {
		List<JLabel> labels = new ArrayList<>();
		for (Component component : container.getComponents()) {
			if (component instanceof JLabel label) {
				labels.add(label);
			}
			if (component instanceof Container child) {
				labels.addAll(collectLabels(child));
			}
		}
		return labels;
	}

	private PaletteFixture palette(ThemeManager.ThemeType theme) {
		return switch (theme) {
			case DARK, LIGHT, DRACULA, DRACULA_LIGHT, BROWN, BROWN_LIGHT -> {
				io.simpleide.ui.ThemePalette p = ThemePalette.of(theme);
				yield new PaletteFixture(p.background(), p.foreground(), p.selectionBackground(),
						p.selectionForeground(), p.inputBackground(), p.buttonBackground(), p.popupBackground(),
						p.border());
			}
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
			.isGreaterThanOrEqualTo(3.0d);
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
			.isEqualTo(new Color(0xFF79C6));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.DATA_TYPE).foreground).isEqualTo(new Color(0x8BE9FD));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.IDENTIFIER).foreground).isEqualTo(new Color(0x8BE9FD));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.FUNCTION).foreground).isEqualTo(new Color(0x50FA7B));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD_2).foreground)
			.isEqualTo(new Color(0xBD93F9));
		assertThat(ThemeColors.contrastRatio(editor.getSyntaxScheme().getStyle(TokenTypes.COMMENT_MULTILINE).foreground,
				editor.getBackground()))
			.isGreaterThanOrEqualTo(3.0d);
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_FLOAT).foreground)
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.DRACULA).syntax().number());
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.MARKUP_TAG_NAME).foreground)
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.DRACULA).syntax().markupTag());
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.ERROR_CHAR).foreground)
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.DRACULA).syntax().error());
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.VARIABLE).foreground).isEqualTo(new Color(0xFFB86C));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground)
			.isEqualTo(new Color(0xF1FA8C));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_NUMBER_DECIMAL_INT).foreground)
			.isEqualTo(new Color(0xBD93F9));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.SEPARATOR).foreground)
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.DRACULA).syntax().separator());
		assertThat(editor.getMatchedBracketBGColor()).isEqualTo(new Color(0x44475A));
		assertThat(editor.getMatchedBracketBorderColor())
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.DRACULA).accent());
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
			.isGreaterThanOrEqualTo(3.0d);
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.FUNCTION).foreground).isEqualTo(new Color(0xB8BB26));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.IDENTIFIER).foreground).isEqualTo(new Color(0x83A598));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.ANNOTATION).foreground).isEqualTo(new Color(0xFE8019));
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.LITERAL_STRING_DOUBLE_QUOTE).foreground)
			.isEqualTo(new Color(0xB8BB26));
		assertThat(UIManager.getColor("Tree.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("MenuBar.hoverBackground")).isEqualTo(new Color(0x504945));
		assertThat(UIManager.getColor("TextPane.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("MenuBar.background")).isEqualTo(new Color(0x282828));
		assertThat(UIManager.getColor("PopupMenu.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN).popupBackground());
		assertThat(UIManager.getColor("OptionPane.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN).surface());
		assertThat(UIManager.getColor("Button.background")).isEqualTo(new Color(0x3C3836));
		LogPanel logPanel = new LogPanel();
		SwingUtilities.updateComponentTreeUI(logPanel);
		assertThat(logPanel.getBackground()).isEqualTo(new Color(0x282828));

		themeManager.setTheme(ThemeManager.ThemeType.BROWN_LIGHT);
		flushEdt();
		assertThat(editor.getSyntaxScheme().getStyle(TokenTypes.RESERVED_WORD).foreground)
			.isEqualTo(new Color(0x9D0006));
		assertThat(UIManager.getColor("Tree.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).background());
		assertThat(UIManager.getColor("MenuBar.hoverBackground"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).selectionBackground());
		assertThat(UIManager.getColor("TextPane.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).background());
		assertThat(UIManager.getColor("MenuBar.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).menuBarBackground());
		assertThat(UIManager.getColor("PopupMenu.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).popupBackground());
		assertThat(UIManager.getColor("OptionPane.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).surface());
		assertThat(UIManager.getColor("Button.background"))
			.isEqualTo(ThemePalette.of(ThemeManager.ThemeType.BROWN_LIGHT).buttonBackground());
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
