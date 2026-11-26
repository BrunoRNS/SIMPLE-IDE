package io.simpleide.ui.debug;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import io.simpleide.thread.nodes.edt.listeners.MouseNode;
import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.editor.EditorTab;
import io.simpleide.ui.menubar.ThemeManager;

/**
 * Bottom-pane console for language-server diagnostics in the selected editor.
 *
 * <p>
 * Diagnostics can be filtered by severity and double-clicking an entry navigates to its
 * source range. The panel follows the active editor tab and is intentionally separate
 * from the left sidebar views.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class DebugConsolePanel extends JPanel {

	private static final List<DiagnosticSeverity> SEVERITIES = List.of(DiagnosticSeverity.Error,
			DiagnosticSeverity.Warning, DiagnosticSeverity.Information, DiagnosticSeverity.Hint);

	private final DefaultListModel<Diagnostic> model = new DefaultListModel<>();

	private final JList<Diagnostic> diagnosticsList = new JList<>(model);

	private final Map<DiagnosticSeverity, JToggleButton> filters = new EnumMap<>(DiagnosticSeverity.class);

	private final Map<DiagnosticSeverity, Integer> counts = new EnumMap<>(DiagnosticSeverity.class);

	private final JLabel countLabel = new JLabel("0");

	private final JLabel statusLabel = new JLabel("No file open");

	private List<Diagnostic> diagnostics = List.of();

	private String fileName = "";

	private EditorTab currentTab;

	/**
	 * Creates an empty diagnostic console.
	 */
	public DebugConsolePanel() {
		super(new BorderLayout(0, 4));
		setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setOpaque(false);
		JLabel title = new JLabel("DEBUG CONSOLE");
		Font titleFont = UIManager.getFont("Label.font");
		if (titleFont != null) {
			title.setFont(titleFont.deriveFont(Font.BOLD, Math.max(10f, titleFont.getSize2D() - 1f)));
		}
		countLabel
			.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(
							ThemePalette.of(ThemeManager.getInstance().getCurrentTheme()).border(), 1, true),
					BorderFactory.createEmptyBorder(1, 7, 1, 7)));
		header.add(title, BorderLayout.WEST);
		header.add(countLabel, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		JPanel content = new JPanel(new BorderLayout(0, 5));
		content.setOpaque(false);
		content.add(createFilters(), BorderLayout.NORTH);

		diagnosticsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		diagnosticsList.setFixedCellHeight(56);
		diagnosticsList.setCellRenderer(new DiagnosticRenderer());
		JScrollPane scrollPane = new JScrollPane(diagnosticsList);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		content.add(scrollPane, BorderLayout.CENTER);

		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 1, 0, 1));
		content.add(statusLabel, BorderLayout.SOUTH);
		add(content, BorderLayout.CENTER);

		diagnosticsList.addMouseListener(new MouseNode().onClick(event -> {
			if (event.getClickCount() == 2) {
				navigateSelected();
			}
		}));
	}

	/**
	 * Follows the editor tab selected in the editor manager.
	 * @param tab selected tab, or {@code null} when the welcome page is selected
	 */
	public void showTab(EditorTab tab) {
		if (currentTab != tab) {
			currentTab = tab;
		}
		refresh();
	}

	/**
	 * Refreshes the console when diagnostics changed for the selected tab.
	 * @param tab tab whose diagnostics changed
	 */
	public void refresh(EditorTab tab) {
		if (tab != null && tab == currentTab) {
			refresh();
		}
	}

	private JPanel createFilters() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		panel.setOpaque(false);
		for (DiagnosticSeverity severity : SEVERITIES) {
			JToggleButton filter = new JToggleButton(filterLabel(severity) + " 0");
			filter.setSelected(true);
			filter.setFocusable(false);
			filter.setMargin(new Insets(3, 8, 3, 8));
			filter.addActionListener(event -> applyFilters());
			filters.put(severity, filter);
			panel.add(filter);
		}
		return panel;
	}

	private void refresh() {
		if (currentTab == null) {
			diagnostics = List.of();
			fileName = "";
			countLabel.setText("0");
			updateCounts();
			applyFilters();
			statusLabel.setText("No file open");
			return;
		}
		fileName = currentTab.getFile().getName();
		List<Diagnostic> current = new ArrayList<>(currentTab.getDiagnostics());
		current.sort(Comparator.comparingInt(DebugConsolePanel::startLine)
			.thenComparingInt(DebugConsolePanel::startCharacter));
		diagnostics = List.copyOf(current);
		countLabel.setText(Integer.toString(diagnostics.size()));
		updateCounts();
		applyFilters();
	}

	private void updateCounts() {
		counts.clear();
		for (DiagnosticSeverity severity : SEVERITIES) {
			counts.put(severity, 0);
		}
		for (Diagnostic diagnostic : diagnostics) {
			DiagnosticSeverity severity = normalizedSeverity(diagnostic);
			counts.merge(severity, 1, Integer::sum);
		}
		for (Map.Entry<DiagnosticSeverity, JToggleButton> entry : filters.entrySet()) {
			entry.getValue().setText(filterLabel(entry.getKey()) + " " + counts.get(entry.getKey()));
		}
	}

	private void applyFilters() {
		model.clear();
		int visible = 0;
		for (Diagnostic diagnostic : diagnostics) {
			JToggleButton filter = filters.get(normalizedSeverity(diagnostic));
			if (filter == null || filter.isSelected()) {
				model.addElement(diagnostic);
				visible++;
			}
		}
		if (currentTab != null) {
			statusLabel.setText(diagnostics.isEmpty() ? fileName + " - no problems"
					: visible + " of " + diagnostics.size() + " problems shown");
		}
	}

	private void navigateSelected() {
		Diagnostic diagnostic = diagnosticsList.getSelectedValue();
		if (currentTab != null && diagnostic != null && diagnostic.getRange() != null) {
			currentTab.goToRange(diagnostic.getRange());
		}
	}

	private static String filterLabel(DiagnosticSeverity severity) {
		return switch (severity) {
			case Error -> "Errors";
			case Warning -> "Warnings";
			case Information -> "Info";
			case Hint -> "Hints";
		};
	}

	private static DiagnosticSeverity normalizedSeverity(Diagnostic diagnostic) {
		return diagnostic.getSeverity() == null ? DiagnosticSeverity.Information : diagnostic.getSeverity();
	}

	private static int startLine(Diagnostic diagnostic) {
		if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
			return 0;
		}
		return Math.max(0, diagnostic.getRange().getStart().getLine());
	}

	private static int startCharacter(Diagnostic diagnostic) {
		if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
			return 0;
		}
		return Math.max(0, diagnostic.getRange().getStart().getCharacter());
	}

	private final class DiagnosticRenderer extends JPanel implements ListCellRenderer<Diagnostic> {

		private final JLabel severityLabel = new JLabel();

		private final JLabel messageLabel = new JLabel();

		private final JLabel locationLabel = new JLabel();

		DiagnosticRenderer() {
			super(new BorderLayout(10, 0));
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0,
							ThemePalette.of(ThemeManager.getInstance().getCurrentTheme()).border()),
					BorderFactory.createEmptyBorder(6, 2, 6, 4)));
			JPanel text = new JPanel(new BorderLayout(0, 3));
			text.setOpaque(false);
			text.add(messageLabel, BorderLayout.CENTER);
			text.add(locationLabel, BorderLayout.SOUTH);
			add(severityLabel, BorderLayout.WEST);
			add(text, BorderLayout.CENTER);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Diagnostic> list, Diagnostic diagnostic,
				int index, boolean selected, boolean cellHasFocus) {
			ThemePalette palette = ThemePalette.of(ThemeManager.getInstance().getCurrentTheme());
			Color background = selected ? UIManager.getColor("List.selectionBackground") : palette.surface();
			Color foreground = selected ? UIManager.getColor("List.selectionForeground") : palette.foreground();
			if (background == null) {
				background = palette.surface();
			}
			if (foreground == null) {
				foreground = palette.foreground();
			}
			setBackground(background);
			setOpaque(true);
			DiagnosticSeverity severity = normalizedSeverity(diagnostic);
			Color semanticColor = severityColor(severity);
			Color readableSeverity = ThemeColors.readable(semanticColor, background);
			severityLabel.setText(filterLabel(severity).toUpperCase(java.util.Locale.ROOT));
			severityLabel.setForeground(readableSeverity);
			severityLabel.setBackground(ThemeColors.blend(readableSeverity, background, 0.84f));
			severityLabel.setOpaque(true);
			severityLabel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
			messageLabel.setText(diagnostic.getMessage() == null || diagnostic.getMessage().isBlank() ? "(no message)"
					: diagnostic.getMessage().strip());
			messageLabel.setForeground(foreground);
			messageLabel.setToolTipText(messageLabel.getText());
			locationLabel
				.setText(fileName + ":" + (startLine(diagnostic) + 1) + ":" + (startCharacter(diagnostic) + 1));
			locationLabel.setForeground(selected ? foreground : palette.mutedForeground());
			locationLabel
				.setFont(locationLabel.getFont().deriveFont(Math.max(9f, locationLabel.getFont().getSize2D() - 1f)));
			return this;
		}

		private Color severityColor(DiagnosticSeverity severity) {
			return switch (severity) {
				case Error -> new Color(0xE0, 0x6C, 0x75);
				case Warning -> new Color(0xD1, 0x9A, 0x66);
				case Information -> new Color(0x61, 0xAF, 0xEF);
				case Hint -> new Color(0x98, 0xC3, 0x79);
			};
		}

	}

}
