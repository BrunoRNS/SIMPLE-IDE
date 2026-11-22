package io.simpleide.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import io.simpleide.thread.nodes.edt.listeners.MouseNode;
import io.simpleide.ui.editor.EditorTab;

/**
 * Debug console that lists the language server diagnostics of the selected editor.
 *
 * <p>
 * The panel follows the tab selected in the editor manager. Each entry shows the
 * severity, the line and column, and the diagnostic message. A double click moves the
 * caret to the reported range. The model is refreshed on the Event Dispatch Thread by the
 * listener the editor manager invokes.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class DebugConsolePanel extends JPanel {

	private final DefaultListModel<String> model = new DefaultListModel<>();

	private final JList<String> diagnosticsList = new JList<>(model);

	private final List<Diagnostic> diagnostics = new ArrayList<>();

	private final JLabel statusLabel = new JLabel("No file open");

	private EditorTab currentTab;

	/**
	 * Creates an empty debug console.
	 */
	public DebugConsolePanel() {
		super(new BorderLayout());
		JLabel title = new JLabel("DEBUG CONSOLE");
		title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		Font titleFont = UIManager.getFont("Label.font");
		if (titleFont != null) {
			title.setFont(titleFont.deriveFont(Font.BOLD, Math.max(10f, titleFont.getSize2D() - 1f)));
		}
		add(title, BorderLayout.NORTH);

		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 12, 6, 12));
		JScrollPane scrollPane = new JScrollPane(diagnosticsList);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		JPanel content = new JPanel(new BorderLayout());
		content.add(statusLabel, BorderLayout.NORTH);
		content.add(scrollPane, BorderLayout.CENTER);
		add(content, BorderLayout.CENTER);

		diagnosticsList.addMouseListener(new MouseNode().onClick(event -> {
			if (event.getClickCount() != 2 || currentTab == null) {
				return;
			}
			int index = diagnosticsList.locationToIndex(event.getPoint());
			if (index >= 0 && index < diagnostics.size()) {
				currentTab.goToRange(diagnostics.get(index).getRange());
			}
		}));
	}

	/**
	 * Follows the editor tab selected in the editor manager.
	 * @param tab selected tab, or {@code null} when the welcome page is selected
	 */
	public void showTab(EditorTab tab) {
		if (currentTab == tab) {
			refresh();
			return;
		}
		currentTab = tab;
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

	private void refresh() {
		model.clear();
		diagnostics.clear();
		if (currentTab == null) {
			statusLabel.setText("No file open");
			return;
		}
		List<Diagnostic> current = new ArrayList<>(currentTab.getDiagnostics());
		current.sort(Comparator.comparingInt(DebugConsolePanel::startLine)
			.thenComparingInt(DebugConsolePanel::startCharacter));
		String fileName = currentTab.getFile().getName();
		for (Diagnostic diagnostic : current) {
			diagnostics.add(diagnostic);
			model.addElement(format(fileName, diagnostic));
		}
		if (current.isEmpty()) {
			statusLabel.setText(fileName + " - no problems");
		}
		else {
			statusLabel.setText(fileName + " - " + current.size() + (current.size() == 1 ? " problem" : " problems"));
		}
	}

	@SuppressWarnings("")
	private static String format(String fileName, Diagnostic diagnostic) {
		int line = startLine(diagnostic) + 1;
		int column = startCharacter(diagnostic) + 1;
		String severity = severityLabel(diagnostic.getSeverity());
		String message = diagnostic.getMessage() == null ? "" : diagnostic.getMessage().strip();
		return severity + "  " + fileName + ":" + line + ":" + column + "  " + message;
	}

	@SuppressWarnings("")
	private static int startLine(Diagnostic diagnostic) {
		if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
			return 0;
		}
		return Math.max(0, diagnostic.getRange().getStart().getLine());
	}

	@SuppressWarnings("")
	private static int startCharacter(Diagnostic diagnostic) {
		if (diagnostic.getRange() == null || diagnostic.getRange().getStart() == null) {
			return 0;
		}
		return Math.max(0, diagnostic.getRange().getStart().getCharacter());
	}

	private static String severityLabel(DiagnosticSeverity severity) {
		if (severity == null) {
			return "INFO";
		}
		return switch (severity) {
			case Error -> "ERROR";
			case Warning -> "WARNING";
			case Information -> "INFO";
			case Hint -> "HINT";
		};
	}

}
