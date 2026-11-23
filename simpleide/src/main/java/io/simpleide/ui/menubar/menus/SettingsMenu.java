package io.simpleide.ui.menubar.menus;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.SettingsService;
import io.simpleide.thread.nodes.edt.listeners.ChangeNode;

/**
 * Settings menu with the theme selector and independent interface and editor font sizes.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class SettingsMenu extends JMenu {

	private ThemeMenu themeMenu;

	private final SettingsService settingsService;

	private JSpinner uiFontSizeSpinner;

	private JSpinner editorFontSizeSpinner;

	/**
	 * Creates the settings menu and its controls.
	 */
	public SettingsMenu() {
		this(SettingsService.getInstance());
	}

	SettingsMenu(SettingsService settingsService) {
		super("Settings");
		this.settingsService = settingsService;
		createItems();
		configureActions();
		configureMenu();
		settingsService.addListener(this::refresh);
	}

	private void configureMenu() {
		add(themeMenu);
		addSeparator();
		add(fontSizeRow("Interface size", "interfaceFontSizeRow", uiFontSizeSpinner));
		add(fontSizeRow("Editor size", "editorFontSizeRow", editorFontSizeSpinner));
	}

	private JPanel fontSizeRow(String labelText, String name, JSpinner spinner) {
		JPanel row = new JPanel(new BorderLayout(10, 0));
		row.setName(name);
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

		JLabel label = new JLabel(labelText);
		label.setName(name + "Label");
		row.add(label, BorderLayout.LINE_START);

		Dimension spinnerSize = spinner.getPreferredSize();
		spinner.setPreferredSize(new Dimension(64, spinnerSize.height));
		row.add(spinner, BorderLayout.LINE_END);
		return row;
	}

	private void configureActions() {
		uiFontSizeSpinner.addChangeListener(new ChangeNode(e -> updateUiFontSize()));
		editorFontSizeSpinner.addChangeListener(new ChangeNode(e -> updateEditorFontSize()));
	}

	private void createItems() {
		IdeSettings settings = settingsService.getSettings();
		themeMenu = new ThemeMenu();
		uiFontSizeSpinner = new JSpinner(new SpinnerNumberModel(settings.getUiFontSize(), 8, 48, 1));
		uiFontSizeSpinner.setName("uiFontSizeSpinner");
		editorFontSizeSpinner = new JSpinner(new SpinnerNumberModel(settings.getEditorFontSize(), 8, 72, 1));
		editorFontSizeSpinner.setName("editorFontSizeSpinner");
	}

	private void updateUiFontSize() {
		int size = ((Number) uiFontSizeSpinner.getValue()).intValue();
		if (size != settingsService.getSettings().getUiFontSize()) {
			settingsService.update(settings -> settings.setUiFontSize(size));
		}
	}

	private void updateEditorFontSize() {
		int size = ((Number) editorFontSizeSpinner.getValue()).intValue();
		if (size != settingsService.getSettings().getEditorFontSize()) {
			settingsService.update(settings -> settings.setEditorFontSize(size));
		}
	}

	private void refresh(IdeSettings settings) {
		uiFontSizeSpinner.setValue(settings.getUiFontSize());
		editorFontSizeSpinner.setValue(settings.getEditorFontSize());
	}

}
