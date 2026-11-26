package io.simpleide.ui.menubar;

import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import io.simpleide.ui.SyntaxPalette;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.ThemePalette;

/**
 * Applies the shared Swing look-and-feel defaults for a selected theme.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class ThemeUIDefaults {

	private ThemeUIDefaults() {
	}

	public static void apply(ThemeManager.ThemeType theme, Font uiFont, Font editorFont) {
		ThemePalette p = ThemePalette.of(theme);
		SyntaxPalette syntax = p.syntax();
		UIDefaults d = UIManager.getLookAndFeelDefaults();

		ThemeUIDefaultPropertyWriter.put(d, "Component.accentColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Component.focusColor",
				ThemeUIDefaultPropertyWriter.alpha(p.accent(), 0x33));
		ThemeUIDefaultPropertyWriter.put(d, "Component.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Component.disabledBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Component.focusedBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Component.error.borderColor", syntax.error());
		ThemeUIDefaultPropertyWriter.put(d, "Component.error.focusedBorderColor", syntax.error());
		ThemeUIDefaultPropertyWriter.put(d, "Component.warning.borderColor", syntax.string());
		ThemeUIDefaultPropertyWriter.put(d, "Component.warning.focusedBorderColor", syntax.string());
		ThemeUIDefaultPropertyWriter.put(d, "Component.success.borderColor", syntax.function());
		ThemeUIDefaultPropertyWriter.put(d, "Component.success.focusedBorderColor", syntax.function());
		ThemeUIDefaultPropertyWriter.put(d, "Component.focusWidth", 0);
		ThemeUIDefaultPropertyWriter.put(d, "Component.innerFocusWidth", 0);
		ThemeUIDefaultPropertyWriter.put(d, "Component.borderWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "Component.arc", 12);
		ThemeUIDefaultPropertyWriter.put(d, "Component.minimumWidth", 0);
		ThemeUIDefaultPropertyWriter.put(d, "Component.arrowType", "chevron");
		ThemeUIDefaultPropertyWriter.put(d, "Component.hideMnemonics", true);

		ThemeUIDefaultPropertyWriter.put(d, "control", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "controlText", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "controlShadow", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "controlDkShadow", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "controlHighlight", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "controlLtHighlight", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "text", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "textText", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "textHighlight", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "textHighlightText", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "textInactiveText", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "info", p.tooltipBackground());
		ThemeUIDefaultPropertyWriter.put(d, "infoText", p.tooltipForeground());
		ThemeUIDefaultPropertyWriter.put(d, "menu", p.popupBackground());
		ThemeUIDefaultPropertyWriter.put(d, "menuText", p.popupForeground());
		ThemeUIDefaultPropertyWriter.put(d, "window", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "windowText", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "desktop", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "activeCaption", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "activeCaptionText", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "inactiveCaption", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "inactiveCaptionText", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "scrollbar", p.scrollbarTrack());

		ThemeUIDefaultPropertyWriter.put(d, "Panel.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Panel.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Label.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Label.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Label.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TitledBorder.titleColor", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "TitledBorder.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Separator.foreground", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Separator.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Separator.height", 1);
		ThemeUIDefaultPropertyWriter.put(d, "Separator.stripeWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "Separator.stripeIndent", 1);

		ThemeUIDefaultPropertyWriter.put(d, "SplitPane.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPane.dividerSize", 10);
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.style", "plain");
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.gripColor", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.hoverColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.pressedColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.draggingColor",
				ThemeColors.blend(p.accent(), p.background(), 0.45f));
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.oneTouchArrowColor", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.oneTouchHoverArrowColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.oneTouchPressedArrowColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.gripDotCount", 3);
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.gripDotSize", 4);
		ThemeUIDefaultPropertyWriter.put(d, "SplitPaneDivider.gripGap", 2);

		ThemeUIDefaultPropertyWriter.put(d, "Viewport.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollPane.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollPane.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollPane.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollPane.arc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "ScrollPane.fillUpperCorner", true);
		ThemeUIDefaultPropertyWriter.put(d, "ScrollPane.smoothScrolling", true);
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.track", p.scrollbarTrack());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.thumb", p.scrollbarThumb());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.hoverThumbColor", p.scrollbarHoverThumb());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.pressedThumbColor", p.scrollbarHoverThumb());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.hoverTrackColor", p.scrollbarTrack());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.pressedTrackColor", p.scrollbarTrack());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.width", 10);
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.thumbArc", 999);
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.trackArc", 0);
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.trackInsets", new Insets(0, 0, 0, 0));
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.showButtons", false);
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.buttonArrowColor", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.buttonDisabledArrowColor", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.hoverButtonBackground", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "ScrollBar.pressedButtonBackground", p.surface());

		ThemeUIDefaultPropertyWriter.putTextLike(d, "TextField", p.inputBackground(), p.inputForeground(),
				p.selectionBackground(), p.selectionForeground(), p.inputForeground(), p.disabledForeground(),
				p.border(), p.border(), new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.putTextLike(d, "FormattedTextField", p.inputBackground(), p.inputForeground(),
				p.selectionBackground(), p.selectionForeground(), p.inputForeground(), p.disabledForeground(),
				p.border(), p.border(), new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.putTextLike(d, "PasswordField", p.inputBackground(), p.inputForeground(),
				p.selectionBackground(), p.selectionForeground(), p.inputForeground(), p.disabledForeground(),
				p.border(), p.border(), new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.putTextLike(d, "TextArea", p.background(), p.foreground(), p.selectionBackground(),
				p.selectionForeground(), p.foreground(), p.disabledForeground(), p.border(), p.border(),
				new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.putTextLike(d, "TextPane", p.background(), p.foreground(), p.selectionBackground(),
				p.selectionForeground(), p.foreground(), p.disabledForeground(), p.border(), p.border(),
				new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.putTextLike(d, "EditorPane", p.background(), p.foreground(),
				p.selectionBackground(), p.selectionForeground(), p.foreground(), p.disabledForeground(), p.border(),
				p.border(), new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.put(d, "TextField.placeholderForeground", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "FormattedTextField.placeholderForeground", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "PasswordField.placeholderForeground", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TextField.arc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "FormattedTextField.arc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "PasswordField.arc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "TextArea.arc", 8);
		ThemeUIDefaultPropertyWriter.put(d, "TextPane.arc", 8);
		ThemeUIDefaultPropertyWriter.put(d, "EditorPane.arc", 8);

		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.background", p.menuBarBackground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.hoverBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.hoverForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.selectionBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.selectionForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.itemMargins", new Insets(3, 8, 3, 8));
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.selectionInsets", new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.put(d, "MenuBar.selectionArc", 8);
		ThemeUIDefaultPropertyWriter.putMenuLike(d, "Menu", p);
		ThemeUIDefaultPropertyWriter.putMenuLike(d, "MenuItem", p);
		ThemeUIDefaultPropertyWriter.putMenuLike(d, "CheckBoxMenuItem", p);
		ThemeUIDefaultPropertyWriter.putMenuLike(d, "RadioButtonMenuItem", p);
		ThemeUIDefaultPropertyWriter.put(d, "MenuItem.underlineSelectionBackground", p.menuSelectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "MenuItem.underlineSelectionColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "MenuItem.underlineSelectionHeight", 2);
		ThemeUIDefaultPropertyWriter.put(d, "Menu.arrowType", "chevron");
		ThemeUIDefaultPropertyWriter.put(d, "MenuItem.arrowType", "chevron");
		ThemeUIDefaultPropertyWriter.put(d, "CheckBoxMenuItem.arrowType", "chevron");
		ThemeUIDefaultPropertyWriter.put(d, "RadioButtonMenuItem.arrowType", "chevron");
		ThemeUIDefaultPropertyWriter.put(d, "CheckBoxMenuItem.icon.checkmarkColor", p.menuSelectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "RadioButtonMenuItem.icon.checkmarkColor", p.menuSelectionForeground());

		ThemeUIDefaultPropertyWriter.put(d, "Popup.background", p.popupBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Popup.foreground", p.popupForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Popup.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Popup.borderWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "Popup.borderCornerRadius", 12);
		ThemeUIDefaultPropertyWriter.put(d, "Popup.dropShadowPainted", true);
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenu.background", p.popupBackground());
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenu.foreground", p.popupForeground());
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenu.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenu.borderWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenu.borderCornerRadius", 12);
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenu.borderInsets", new Insets(4, 1, 4, 1));
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenu.separatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenuSeparator.stripeWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenuSeparator.stripeIndent", 4);
		ThemeUIDefaultPropertyWriter.put(d, "PopupMenuSeparator.height", 9);
		ThemeUIDefaultPropertyWriter.put(d, "ToolTip.background", p.tooltipBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ToolTip.foreground", p.tooltipForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ToolTip.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ToolTip.borderWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "ToolTip.borderCornerRadius", 10);
		ThemeUIDefaultPropertyWriter.put(d, "ToolTip.margin", new Insets(4, 8, 4, 8));

		ThemeUIDefaultPropertyWriter.put(d, "Button.background", p.buttonBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.foreground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.disabledBackground", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Button.disabledText", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.hoverBackground", p.buttonHoverBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.pressedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.selectedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.selectedForeground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.default.background", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Button.default.foreground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.default.hoverBackground", p.buttonHoverBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.default.pressedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.default.borderColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Button.default.focusedBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Button.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Button.focusedBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Button.hoverBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Button.disabledBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Button.arc", 12);
		ThemeUIDefaultPropertyWriter.put(d, "Button.borderWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "Button.margin", new Insets(2, 14, 2, 14));
		ThemeUIDefaultPropertyWriter.put(d, "Button.minimumWidth", 72);
		ThemeUIDefaultPropertyWriter.put(d, "Button.iconTextGap", 4);
		ThemeUIDefaultPropertyWriter.put(d, "Button.toolbar.hoverBackground", p.buttonHoverBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.toolbar.pressedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Button.toolbar.selectedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.background", p.buttonBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.foreground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.hoverBackground", p.buttonHoverBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.pressedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.selectedBackground", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.selectedForeground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.disabledBackground", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.disabledText", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ToggleButton.disabledSelectedBackground", p.buttonBackground());
		ThemeUIDefaultPropertyWriter.putCheckBoxLike(d, "CheckBox", p);
		ThemeUIDefaultPropertyWriter.putCheckBoxLike(d, "RadioButton", p);

		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.background", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.foreground", p.inputForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.disabledBackground", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonBackground", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonEditableBackground", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonSeparatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonDisabledSeparatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonArrowColor", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonDisabledArrowColor", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonHoverArrowColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.buttonPressedArrowColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.selectionBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.selectionForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.popupBackground", p.popupBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.popupForeground", p.popupForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.arc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.focusedBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.disabledBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.editableBackground", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.focusedBackground", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.selectionInsets", new Insets(2, 4, 2, 4));
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.selectionArc", 8);
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.padding", new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.minimumWidth", 72);
		ThemeUIDefaultPropertyWriter.put(d, "ComboBox.arrowType", "chevron");

		ThemeUIDefaultPropertyWriter.put(d, "Spinner.background", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.foreground", p.inputForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.disabledBackground", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonBackground", p.inputBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonSeparatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonDisabledSeparatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonArrowColor", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonDisabledArrowColor", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonHoverArrowColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonPressedArrowColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.focusedBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.disabledBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.arc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.padding", new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.put(d, "Spinner.buttonStyle", "button");

		ThemeUIDefaultPropertyWriter.put(d, "List.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "List.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "List.selectionBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "List.selectionForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "List.selectionInactiveBackground", p.inactiveSelectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "List.selectionInactiveForeground", p.inactiveSelectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "List.cellFocusColor", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "List.selectionArc", 8);
		ThemeUIDefaultPropertyWriter.put(d, "List.selectionInsets", new Insets(2, 4, 2, 4));
		ThemeUIDefaultPropertyWriter.put(d, "List.cellMargins", new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.put(d, "List.rowHeight", 22);
		ThemeUIDefaultPropertyWriter.put(d, "Table.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Table.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Table.selectionBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Table.selectionForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Table.selectionInactiveBackground", p.inactiveSelectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Table.selectionInactiveForeground", p.inactiveSelectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Table.gridColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Table.showHorizontalLines", false);
		ThemeUIDefaultPropertyWriter.put(d, "Table.showVerticalLines", false);
		ThemeUIDefaultPropertyWriter.put(d, "Table.intercellSpacing", new java.awt.Dimension(0, 0));
		ThemeUIDefaultPropertyWriter.put(d, "Table.rowHeight", 22);
		ThemeUIDefaultPropertyWriter.put(d, "Table.cellFocusColor", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Table.cellMargins", new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.put(d, "Table.focusCellBackground", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Table.focusCellForeground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.background", p.headerBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.foreground", p.headerForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.hoverBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.hoverForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.pressedBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.pressedForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.separatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.bottomSeparatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.height", 26);
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.cellMargins", new Insets(2, 6, 2, 6));
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.sortIconColor", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TableHeader.focusCellBackground", p.headerBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.textBackground", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.textForeground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.selectionBackground", p.treeSelectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.selectionForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.selectionInactiveBackground", p.inactiveSelectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.selectionInactiveForeground", p.inactiveSelectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.selectionBorderColor", p.treeSelectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.hash", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.selectionArc", 8);
		ThemeUIDefaultPropertyWriter.put(d, "Tree.selectionInsets", new Insets(2, 4, 2, 4));
		ThemeUIDefaultPropertyWriter.put(d, "Tree.rowHeight", 22);
		ThemeUIDefaultPropertyWriter.put(d, "Tree.rendererMargins", new Insets(2, 4, 2, 4));
		ThemeUIDefaultPropertyWriter.put(d, "Tree.paintLines", false);
		ThemeUIDefaultPropertyWriter.put(d, "Tree.showDefaultIcons", false);
		ThemeUIDefaultPropertyWriter.put(d, "Tree.wideSelection", true);
		ThemeUIDefaultPropertyWriter.put(d, "Tree.dropLineColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.dropCellBackground", p.treeSelectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.dropCellForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.icon.expandedColor", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.icon.collapsedColor", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.icon.leafColor", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.icon.closedColor", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Tree.icon.openColor", p.mutedForeground());

		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.selectedForeground", p.tabSelectedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.selectedBackground", p.tabSelectedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.disabledForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.disabledBackground", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.hoverForeground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.hoverColor", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.focusColor", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.underlineColor", p.tabUnderline());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.inactiveUnderlineColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.disabledUnderlineColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.contentAreaColor", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabSeparatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.showTabSeparators", true);
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabSeparatorsFullHeight", false);
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.showContentSeparator", true);
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabHeight", 32);
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabSelectionHeight", 2);
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabInsets", new Insets(4, 12, 4, 12));
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabArc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabSelectionArc", 10);
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.tabType", "underlined");
		ThemeUIDefaultPropertyWriter.put(d, "TabbedPane.hasFullBorder", false);

		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.hoverBackground", p.buttonHoverBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.pressedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.hoverForeground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.pressedForeground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.separatorColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.separatorWidth", 1);
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.margin", new Insets(2, 2, 2, 2));
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.spacingInsets", new Insets(2, 2, 2, 2));
		ThemeUIDefaultPropertyWriter.put(d, "ToolBar.gripColor", p.mutedForeground());

		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.messageForeground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.buttonBackground", p.buttonBackground());
		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.buttonForeground", p.buttonForeground());
		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.border", BorderFactory.createEmptyBorder(12, 12, 12, 12));
		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.messageAreaBorder",
				BorderFactory.createEmptyBorder(0, 0, 0, 0));
		ThemeUIDefaultPropertyWriter.put(d, "OptionPane.buttonAreaBorder",
				BorderFactory.createEmptyBorder(12, 0, 0, 0));
		ThemeUIDefaultPropertyWriter.put(d, "FileChooser.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "FileChooser.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "FileChooser.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "FileChooser.focusColor", p.focusBorder());
		ThemeUIDefaultPropertyWriter.put(d, "FileChooser.readOnlyForeground", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "ColorChooser.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "ColorChooser.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "ColorChooser.swatchBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "ColorChooser.swatchSelectedBorderColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "ColorChooser.swatchFocusColor", p.focusBorder());
		ThemeUIDefaultPropertyWriter.put(d, "ColorChooser.swatchesDefaultRecentColor", p.surface());

		ThemeUIDefaultPropertyWriter.put(d, "ProgressBar.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "ProgressBar.foreground", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "ProgressBar.selectionBackground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "ProgressBar.selectionForeground", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "ProgressBar.arc", 6);
		ThemeUIDefaultPropertyWriter.put(d, "ProgressBar.horizontalSize", new java.awt.Dimension(146, 4));
		ThemeUIDefaultPropertyWriter.put(d, "ProgressBar.verticalSize", new java.awt.Dimension(4, 146));
		ThemeUIDefaultPropertyWriter.put(d, "Slider.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.thumbColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.trackColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.trackValueColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.hoverThumbColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.pressedThumbColor", p.accent());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.disabledTrackColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.disabledThumbColor", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.tickColor", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Slider.focusColor", ThemeUIDefaultPropertyWriter.alpha(p.accent(), 0x33));
		ThemeUIDefaultPropertyWriter.put(d, "Slider.trackWidth", 4);
		ThemeUIDefaultPropertyWriter.put(d, "Slider.thumbSize", new java.awt.Dimension(14, 14));
		ThemeUIDefaultPropertyWriter.put(d, "Slider.thumbArc", 999);
		ThemeUIDefaultPropertyWriter.put(d, "Slider.trackArc", 999);

		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.background", p.menuBarBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.inactiveBackground", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.inactiveForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.buttonHoverBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.buttonPressedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.buttonForeground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.buttonInactiveForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.buttonHoverForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "TitlePane.buttonPressedForeground", p.selectionForeground());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.foreground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.borderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.activeBorderColor", p.focusBorder());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.inactiveBorderColor", p.border());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.activeTitleBackground", p.menuBarBackground());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.activeTitleForeground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.inactiveTitleBackground", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.inactiveTitleForeground", p.disabledForeground());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.buttonHoverBackground", p.selectionBackground());
		ThemeUIDefaultPropertyWriter.put(d, "InternalFrame.buttonPressedBackground", p.buttonPressedBackground());
		ThemeUIDefaultPropertyWriter.put(d, "DesktopPane.background", p.surface());
		ThemeUIDefaultPropertyWriter.put(d, "RootPane.background", p.background());
		ThemeUIDefaultPropertyWriter.put(d, "RootPane.activeBorderColor", p.focusBorder());
		ThemeUIDefaultPropertyWriter.put(d, "RootPane.inactiveBorderColor", p.border());

		ThemeUIDefaultPropertyWriter.put(d, "Log.traceForeground", p.mutedForeground());
		ThemeUIDefaultPropertyWriter.put(d, "Log.debugForeground", syntax.type());
		ThemeUIDefaultPropertyWriter.put(d, "Log.infoForeground", p.foreground());
		ThemeUIDefaultPropertyWriter.put(d, "Log.warnForeground", syntax.string());
		ThemeUIDefaultPropertyWriter.put(d, "Log.errorForeground", syntax.error());

		applyFontDefaults(d, uiFont, editorFont);
	}

	public static void applyFontDefaults(UIDefaults defaults, Font uiFont, Font editorFont) {
		if (defaults == null) {
			defaults = UIManager.getDefaults();
		}
		List<Object> keys = new ArrayList<>();
		for (Enumeration<Object> enumeration = defaults.keys(); enumeration.hasMoreElements();) {
			keys.add(enumeration.nextElement());
		}
		for (Object key : keys) {
			Object value = defaults.get(key);
			if (value instanceof FontUIResource) {
				defaults.put(key, new FontUIResource(uiFont));
				UIManager.put(key, new FontUIResource(uiFont));
			}
		}
		FontUIResource editorFontResource = new FontUIResource(editorFont);
		ThemeUIDefaultPropertyWriter.put(defaults, "TextArea.font", editorFontResource);
		ThemeUIDefaultPropertyWriter.put(defaults, "RSyntaxTextArea.font", editorFontResource);
	}

}
