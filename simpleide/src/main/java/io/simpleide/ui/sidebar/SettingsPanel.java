package io.simpleide.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import io.simpleide.settings.IdeAction;
import io.simpleide.settings.IdeSettings;
import io.simpleide.settings.KeybindingManager;
import io.simpleide.settings.SettingsService;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.edt.listeners.ChangeNode;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.menubar.ThemeManager;

/**
 * Sidebar view that edits the persistent application settings.
 *
 * <p>
 * The view exposes the theme, the interface and editor fonts, editor behavior options and
 * the shortcut of every {@link IdeAction}. Every change is written through
 * {@link SettingsService}, which persists the JSON file and notifies the rest of the
 * application on the Event Dispatch Thread. Invalid shortcuts are reported before
 * anything is stored.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class SettingsPanel extends JPanel {

	private final SettingsService settingsService;

	private final JComboBox<ThemeManager.ThemeType> themeBox = new JComboBox<>(ThemeManager.ThemeType.values());

	private final JComboBox<String> uiFontFamilyBox = new JComboBox<>();

	private final JComboBox<String> editorFontFamilyBox = new JComboBox<>();

	private final JSpinner uiFontSizeSpinner;

	private final JSpinner editorFontSizeSpinner;

	private final JSpinner tabSizeSpinner;

	private final JCheckBox wordWrapBox = new JCheckBox("Word wrap");

	private final JCheckBox lineNumbersBox = new JCheckBox("Show line numbers");

	private final JCheckBox whitespaceBox = new JCheckBox("Show whitespace");

	private final Map<IdeAction, JTextField> shortcutFields = new EnumMap<>(IdeAction.class);

	private final JLabel feedbackLabel = new JLabel(" ");

	private AutoCompleteSupport uiFontAutoComplete = null;

	/**
	 * Creates the settings view from the current stored settings.
	 */
	public SettingsPanel() {
		this(SettingsService.getInstance());
	}

	SettingsPanel(SettingsService settingsService) {
		super(new BorderLayout());
		this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
		IdeSettings settings = settingsService.getSettings();
		uiFontSizeSpinner = new JSpinner(new SpinnerNumberModel(settings.getUiFontSize(), 8, 48, 1));
		editorFontSizeSpinner = new JSpinner(new SpinnerNumberModel(settings.getEditorFontSize(), 8, 72, 1));
		tabSizeSpinner = new JSpinner(new SpinnerNumberModel(settings.getTabSize(), 1, 16, 1));

		List<JSpinner> lspinners = List.of(uiFontSizeSpinner, editorFontSizeSpinner, tabSizeSpinner);

		for (JSpinner spinner : lspinners) {
			spinner.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
			JComponent editor = spinner.getEditor();
			if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
				defaultEditor.getTextField().setHorizontalAlignment(JTextField.LEFT);
			}
		}

		createHeader();
		createContent();
		configureActions();
		refresh(settings);
		settingsService.addListener(this::refresh);
	}

	private void createHeader() {
		JLabel title = new JLabel("SETTINGS");
		title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		Font titleFont = UIManager.getFont("Label.font");
		if (titleFont != null) {
			title.setFont(titleFont.deriveFont(Font.BOLD, Math.max(10f, titleFont.getSize2D() - 1f)));
		}
		add(title, BorderLayout.NORTH);
	}

	private void createContent() {
		JPanel form = new ScrollablePanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

		List<String> fontFamilies = populateFontFamilies();
		themeBox.setRenderer(new ThemeRenderer());
		enableAutoComplete(uiFontFamilyBox, fontFamilies);
		enableAutoComplete(editorFontFamilyBox, fontFamilies);
		uiFontFamilyBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, uiFontFamilyBox.getPreferredSize().height));
		editorFontFamilyBox
			.setMaximumSize(new Dimension(Integer.MAX_VALUE, editorFontFamilyBox.getPreferredSize().height));

		addSection(form, "Appearance");
		addRow(form, "Theme", themeBox);
		addRow(form, "Interface font", uiFontFamilyBox);
		addRow(form, "Interface size", uiFontSizeSpinner);
		addRow(form, "Editor font", editorFontFamilyBox);
		addRow(form, "Editor size", editorFontSizeSpinner);

		addSection(form, "Editor");
		addRow(form, "Tab size", tabSizeSpinner);
		addRow(form, "", wordWrapBox);
		addRow(form, "", lineNumbersBox);
		addRow(form, "", whitespaceBox);

		addSection(form, "Shortcuts");
		JLabel hint = new JLabel("Format: ctrl shift S");
		hint.setFont(hint.getFont().deriveFont(Font.PLAIN, Math.max(9f, hint.getFont().getSize2D() - 1f)));
		hint.setForeground(secondaryForeground());
		form.add(hint);
		for (IdeAction action : IdeAction.values()) {
			JTextField field = new JTextField(12);
			field.setToolTipText("Default: " + action.getDefaultKeybinding());
			shortcutFields.put(action, field);
			addRow(form, action.getLabel(), field);
		}

		JButton applyShortcuts = new JButton("Apply shortcuts");
		applyShortcuts.setAlignmentX(Component.LEFT_ALIGNMENT);
		applyShortcuts.addActionListener(new ActionNode(event -> applyShortcuts()));
		form.add(Box.createVerticalStrut(6));
		form.add(applyShortcuts);

		addSection(form, "Reset");
		JButton resetButton = new JButton("Reset to defaults");
		resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		resetButton.addActionListener(new ActionNode(event -> {
			settingsService.resetToDefaults();
			feedbackLabel.setText("Defaults restored.");
		}));
		form.add(resetButton);

		feedbackLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		feedbackLabel.setForeground(secondaryForeground());
		form.add(feedbackLabel);

		JScrollPane scrollPane = new JScrollPane(form, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getVerticalScrollBar().setBlockIncrement(100);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		add(scrollPane, BorderLayout.CENTER);
	}

	private List<String> populateFontFamilies() {
		List<String> families = new ArrayList<>();
		try {
			families
				.addAll(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
		}
		catch (RuntimeException error) {
			// Font enumeration is best effort; the current values remain selectable
			// below.
		}
		for (String required : List.of(IdeSettings.DEFAULT_UI_FONT_FAMILY, IdeSettings.DEFAULT_EDITOR_FONT_FAMILY)) {
			if (!families.contains(required)) {
				families.add(required);
			}
		}
		families.sort(String.CASE_INSENSITIVE_ORDER);
		for (String family : families) {
			uiFontFamilyBox.addItem(family);
			editorFontFamilyBox.addItem(family);
		}
		return families;
	}

	/**
	 * Turns an editable combo box into a type-ahead field: while the user types, the
	 * popup shrinks to the items that contain the typed text, and the full list comes
	 * back once the editor loses focus.
	 * @param comboBox combo box to make editable and filterable
	 * @param originalItems every item the combo box offers when no filter is applied
	 */
	private void enableAutoComplete(JComboBox<String> comboBox, List<String> originalItems) {
		this.uiFontAutoComplete = new AutoCompleteSupport(comboBox, originalItems);
	}

	public AutoCompleteSupport getUiFontAutoComplete() {
		return this.uiFontAutoComplete;
	}

	private void addSection(JPanel form, String title) {
		JLabel label = new JLabel(title);
		label.setBorder(BorderFactory.createEmptyBorder(12, 0, 6, 0));
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		form.add(label);
	}

	private void addRow(JPanel form, String labelText, JComponent component) {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (!labelText.isEmpty()) {
			JLabel label = new JLabel(labelText);
			label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.add(label);
		}
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		row.add(component);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		form.add(row);
		form.add(Box.createVerticalStrut(8));
	}

	private void configureActions() {
		themeBox.addActionListener(new ActionNode(event -> updateTheme()));
		uiFontFamilyBox.addActionListener(new ActionNode(event -> updateFonts()));
		editorFontFamilyBox.addActionListener(new ActionNode(event -> updateFonts()));
		uiFontSizeSpinner.addChangeListener(new ChangeNode(event -> updateFonts()));
		editorFontSizeSpinner.addChangeListener(new ChangeNode(event -> updateFonts()));
		tabSizeSpinner.addChangeListener(new ChangeNode(event -> updateTabSize()));
		wordWrapBox.addActionListener(new ActionNode(event -> updateEditorOptions()));
		lineNumbersBox.addActionListener(new ActionNode(event -> updateEditorOptions()));
		whitespaceBox.addActionListener(new ActionNode(event -> updateEditorOptions()));
	}

	public void updateThemeBox() {
		ThemeManager.ThemeType current = ThemeManager.getInstance().getCurrentTheme();
		themeBox.setSelectedItem(current);
		ThemePalette p = ThemePalette.of(current);
		themeBox.setBackground(p.inputBackground());
		themeBox.setForeground(p.inputForeground());
		this.updateTheme();
	}

	private void updateTheme() {
		ThemeManager.ThemeType selected = (ThemeManager.ThemeType) themeBox.getSelectedItem();
		if (selected == null || selected.name().equals(settingsService.getSettings().getTheme())) {
			themeBox.setSelectedItem(ThemeManager.getInstance().getCurrentTheme());
			selected = ThemeManager.getInstance().getCurrentTheme();
		}
		final ThemeManager.ThemeType theme = selected;
		settingsService.update(settings -> settings.setTheme(theme.name()));
		ThemeManager.getInstance().setTheme(selected);
		feedbackLabel.setText("Theme saved.");
		ThemePalette p = ThemePalette.of(ThemeManager.getInstance().getCurrentTheme());
		themeBox.setBackground(p.inputBackground());
		themeBox.setForeground(p.inputForeground());
	}

	private void updateFonts() {
		String uiFamily = selectedFamily(uiFontFamilyBox, settingsService.getSettings().getUiFontFamily());
		String editorFamily = selectedFamily(editorFontFamilyBox, settingsService.getSettings().getEditorFontFamily());
		int uiSize = ((Number) uiFontSizeSpinner.getValue()).intValue();
		int editorSize = ((Number) editorFontSizeSpinner.getValue()).intValue();
		IdeSettings current = settingsService.getSettings();
		if (uiFamily.equals(current.getUiFontFamily()) && editorFamily.equals(current.getEditorFontFamily())
				&& uiSize == current.getUiFontSize() && editorSize == current.getEditorFontSize()) {
			return;
		}
		settingsService.update(settings -> {
			settings.setUiFontFamily(uiFamily);
			settings.setEditorFontFamily(editorFamily);
			settings.setUiFontSize(uiSize);
			settings.setEditorFontSize(editorSize);
		});
		feedbackLabel.setText("Fonts saved.");
	}

	private void updateTabSize() {
		int tabSize = ((Number) tabSizeSpinner.getValue()).intValue();
		if (tabSize == settingsService.getSettings().getTabSize()) {
			return;
		}
		settingsService.update(settings -> settings.setTabSize(tabSize));
		feedbackLabel.setText("Editor settings saved.");
	}

	private void updateEditorOptions() {
		boolean wordWrap = wordWrapBox.isSelected();
		boolean lineNumbers = lineNumbersBox.isSelected();
		boolean whitespace = whitespaceBox.isSelected();
		IdeSettings current = settingsService.getSettings();
		if (wordWrap == current.isWordWrap() && lineNumbers == current.isShowLineNumbers()
				&& whitespace == current.isShowWhitespace()) {
			return;
		}
		settingsService.update(settings -> {
			settings.setWordWrap(wordWrap);
			settings.setShowLineNumbers(lineNumbers);
			settings.setShowWhitespace(whitespace);
		});
		feedbackLabel.setText("Editor settings saved.");
	}

	private void applyShortcuts() {
		List<String> invalid = new ArrayList<>();
		for (Map.Entry<IdeAction, JTextField> entry : shortcutFields.entrySet()) {
			String text = entry.getValue().getText();
			if (text != null && !text.isBlank() && IdeAction.parseKeyStroke(text) == null) {
				invalid.add(entry.getKey().getLabel());
			}
		}
		if (!invalid.isEmpty()) {
			feedbackLabel.setText("Invalid shortcut: " + String.join(", ", invalid));
			return;
		}
		settingsService.update(settings -> {
			for (Map.Entry<IdeAction, JTextField> entry : shortcutFields.entrySet()) {
				KeybindingManager.applyKeybinding(settings, entry.getKey(), entry.getValue().getText());
			}
		});
		feedbackLabel.setText("Shortcuts saved.");
	}

	private void refresh(IdeSettings settings) {
		ThemeManager.ThemeType theme = IdeSettings.parseTheme(settings.getTheme());
		if (theme == null)
			theme = ThemeManager.getInstance().getCurrentTheme();
		themeBox.setSelectedItem(theme);
		ThemePalette p = ThemePalette.of(theme);
		themeBox.setBackground(p.inputBackground());
		themeBox.setForeground(p.inputForeground());
		uiFontFamilyBox.setSelectedItem(settings.getUiFontFamily());
		editorFontFamilyBox.setSelectedItem(settings.getEditorFontFamily());
		uiFontSizeSpinner.setValue(settings.getUiFontSize());
		editorFontSizeSpinner.setValue(settings.getEditorFontSize());
		tabSizeSpinner.setValue(settings.getTabSize());
		wordWrapBox.setSelected(settings.isWordWrap());
		lineNumbersBox.setSelected(settings.isShowLineNumbers());
		whitespaceBox.setSelected(settings.isShowWhitespace());
		for (Map.Entry<IdeAction, JTextField> entry : shortcutFields.entrySet()) {
			entry.getValue().setText(settings.keybindingOf(entry.getKey()));
		}
	}

	private static String selectedFamily(JComboBox<String> box, String fallback) {
		Object selected = box.isEditable() ? box.getEditor().getItem() : box.getSelectedItem();
		if (selected == null || selected.toString().isBlank()) {
			return fallback;
		}
		return selected.toString().trim();
	}

	private java.awt.Color secondaryForeground() {
		java.awt.Color color = UIManager.getColor("Label.disabledForeground");
		return color == null ? UIManager.getColor("Label.foreground") : color;
	}

	/**
	 * Type-ahead support for an editable string combo box: typing filters the popup to
	 * the matching items, the arrow keys walk those matches and the full list is restored
	 * once the editor loses focus.
	 */
	public static final class AutoCompleteSupport implements DocumentListener {

		private final JComboBox<String> comboBox;

		private final JTextComponent editor;

		private final List<String> items;

		private boolean updating;

		private boolean filteringScheduled;

		AutoCompleteSupport(JComboBox<String> comboBox, List<String> items) {
			this.comboBox = comboBox;
			this.items = List.copyOf(items);

			comboBox.setEditable(true);

			this.editor = (JTextComponent) comboBox.getEditor().getEditorComponent();

			this.addListeners();
		}

		private void addListeners() {
			editor.getDocument().addDocumentListener(this);
		}

		@Override
		public void insertUpdate(DocumentEvent event) {
			scheduleFilter();
		}

		@Override
		public void removeUpdate(DocumentEvent event) {
			scheduleFilter();
		}

		@Override
		public void changedUpdate(DocumentEvent event) {
			scheduleFilter();
		}

		private void scheduleFilter() {
			if (updating || filteringScheduled) {
				return;
			}

			filteringScheduled = true;

			javax.swing.SwingUtilities.invokeLater(() -> {
				filteringScheduled = false;
				filter();
			});
		}

		private void filter() {
			if (updating || !editor.hasFocus()) {
				return;
			}

			String typed = editor.getText();
			int caret = editor.getCaretPosition();

			List<String> matches = items.stream()
				.filter(item -> item.toLowerCase(Locale.ROOT).contains(typed.toLowerCase(Locale.ROOT)))
				.toList();

			updating = true;

			try {
				comboBox.setModel(new DefaultComboBoxModel<>(matches.toArray(new String[0])));

				comboBox.setSelectedIndex(-1);

				editor.setText(typed);
				editor.setCaretPosition(Math.min(caret, editor.getDocument().getLength()));

				if (!typed.isBlank() && !matches.isEmpty()) {
					comboBox.showPopup();
				}
				else {
					comboBox.hidePopup();
				}
			}
			finally {
				updating = false;
			}
		}

	}

	private static final class ScrollablePanel extends JPanel implements javax.swing.Scrollable {

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
			return visibleRect.height - 16;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}

	}

	private static final class ThemeRenderer extends javax.swing.DefaultListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			ThemePalette palette = ThemeManager.getInstance().currentPalette();
			boolean popupRow = index >= 0;
			setOpaque(true);
			if (isSelected) {
				setBackground(popupRow ? palette.selectionBackground() : palette.inputBackground());
				setForeground(popupRow ? palette.selectionForeground() : palette.inputForeground());
			}
			else {
				setBackground(popupRow ? palette.popupBackground() : palette.inputBackground());
				setForeground(popupRow ? palette.popupForeground() : palette.inputForeground());
			}
			if (value instanceof ThemeManager.ThemeType theme) {
				String text = theme.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
				setText(Character.toUpperCase(text.charAt(0)) + text.substring(1));
			}
			return this;
		}

	}

}
