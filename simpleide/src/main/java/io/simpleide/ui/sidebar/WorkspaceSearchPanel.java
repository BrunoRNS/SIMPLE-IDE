package io.simpleide.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.search.WorkspaceSearchService;
import io.simpleide.search.WorkspaceSearchService.SearchMatch;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.edt.listeners.MouseNode;
import io.simpleide.thread.nodes.edt.listeners.SwingActionNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.ui.icon.MaterialIcons;

/**
 * Workspace-wide text search view.
 *
 * <p>
 * The view searches every text file below the folder currently open in the Explorer.
 * Results are grouped by file and a double click opens the file at the matching line.
 * Searching runs on a managed background node and the results are applied on the Swing
 * Event Dispatch Thread. All user interaction is routed through the thread manager.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class WorkspaceSearchPanel extends JPanel {

	private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceSearchPanel.class);

	private static final int MAX_PREVIEW_LENGTH = 120;

	private final Supplier<Path> rootSupplier;

	private final BiConsumer<Path, Integer> openMatch;

	private final WorkspaceSearchService searchService = new WorkspaceSearchService();

	private final JTextField queryField = new JTextField(18);

	private final JCheckBox matchCase = new JCheckBox("Match case");

	private final JLabel statusLabel = new JLabel(" ");

	private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Results");

	private final DefaultTreeModel model = new DefaultTreeModel(rootNode);

	private final JTree results = new JTree(model);

	private final JPanel body = new JPanel(new BorderLayout());

	/**
	 * Creates the search view.
	 * @param rootSupplier supplies the folder currently open, or {@code null}
	 * @param openMatch opens a file at a one-based line number
	 */
	public WorkspaceSearchPanel(Supplier<Path> rootSupplier, BiConsumer<Path, Integer> openMatch) {
		super(new BorderLayout());
		this.rootSupplier = rootSupplier;
		this.openMatch = openMatch;
		createHeader();
		createSearchControls();
		createResults();
		add(body, BorderLayout.CENTER);
	}

	/**
	 * Moves the keyboard focus to the search field.
	 */
	public void focusSearchField() {
		queryField.requestFocusInWindow();
		queryField.selectAll();
	}

	private void createHeader() {
		JLabel title = new JLabel("SEARCH");
		title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		Font titleFont = UIManager.getFont("Label.font");
		if (titleFont != null) {
			title.setFont(titleFont.deriveFont(Font.BOLD, Math.max(10f, titleFont.getSize2D() - 1f)));
		}
		add(title, BorderLayout.NORTH);
	}

	private void createSearchControls() {
		JPanel controls = new JPanel(new BorderLayout(0, 8));
		controls.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));

		queryField.putClientProperty("JTextField.placeholderText", "Find in files");
		queryField.setToolTipText("Text to find in the open folder");
		queryField.getAccessibleContext().setAccessibleName("Search workspace");
		queryField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "simpleide.workspaceSearch");
		queryField.getActionMap()
			.put("simpleide.workspaceSearch", new SwingActionNode("Search workspace", this::startSearch));

		JButton searchButton = new JButton("Search");
		searchButton.setToolTipText("Search the open folder");
		searchButton.addActionListener(new ActionNode(event -> startSearch()));
		JPanel queryRow = new JPanel(new BorderLayout(8, 0));
		queryRow.add(queryField, BorderLayout.CENTER);
		queryRow.add(searchButton, BorderLayout.EAST);
		controls.add(queryRow, BorderLayout.NORTH);

		matchCase.setFocusable(false);
		JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		options.add(matchCase);

		statusLabel.setForeground(secondaryForeground());
		JPanel footer = new JPanel(new BorderLayout());
		footer.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		footer.add(options, BorderLayout.WEST);
		footer.add(statusLabel, BorderLayout.CENTER);
		controls.add(footer, BorderLayout.CENTER);

		body.add(controls, BorderLayout.NORTH);
	}

	private void createResults() {
		results.setRootVisible(false);
		results.setShowsRootHandles(true);
		results.setCellRenderer(new SearchResultRenderer());
		results.setBorder(BorderFactory.createEmptyBorder());
		results.addMouseListener(new MouseNode().onClick(event -> {
			if (event.getClickCount() != 2) {
				return;
			}
			TreePath path = results.getPathForLocation(event.getX(), event.getY());
			if (path == null || !(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
				return;
			}
			if (node.getUserObject() instanceof SearchMatch match) {
				openMatch.accept(match.path(), match.line());
			}
		}));

		JScrollPane scrollPane = new JScrollPane(results);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		body.add(scrollPane, BorderLayout.CENTER);
	}

	private void startSearch() {
		Path root = rootSupplier.get();
		String query = queryField.getText();
		if (root == null) {
			clearResults("Open a folder to search the workspace.");
			return;
		}
		if (query == null || query.isBlank()) {
			clearResults("Type something to search.");
			return;
		}

		statusLabel.setText("Searching...");
		boolean caseSensitive = matchCase.isSelected();
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				List<SearchMatch> matches = searchService.search(root, query, caseSensitive,
						WorkspaceSearchService.DEFAULT_MAX_RESULTS);
				threadMaster.executeOnEDT(() -> showResults(matches));
			}));
		}
		catch (RuntimeException error) {
			LOGGER.warn("Unable to start workspace search in {}.", root, error);
			clearResults("Unable to search the workspace.");
		}
	}

	private void showResults(List<SearchMatch> matches) {
		Map<Path, DefaultMutableTreeNode> files = new LinkedHashMap<>();
		rootNode.removeAllChildren();
		for (SearchMatch match : matches) {
			DefaultMutableTreeNode fileNode = files.computeIfAbsent(match.path(), path -> {
				DefaultMutableTreeNode created = new DefaultMutableTreeNode(path);
				rootNode.add(created);
				return created;
			});
			fileNode.add(new DefaultMutableTreeNode(match));
		}
		model.reload();
		for (int row = 0; row < results.getRowCount(); row++) {
			results.expandRow(row);
		}
		if (matches.isEmpty()) {
			statusLabel.setText("No results.");
		}
		else if (matches.size() >= WorkspaceSearchService.DEFAULT_MAX_RESULTS) {
			statusLabel.setText(matches.size() + "+ results (limit reached).");
		}
		else {
			statusLabel.setText(matches.size() + (matches.size() == 1 ? " result." : " results."));
		}
	}

	private void clearResults(String message) {
		rootNode.removeAllChildren();
		model.reload();
		statusLabel.setText(message);
	}

	private Color secondaryForeground() {
		Color color = UIManager.getColor("Label.disabledForeground");
		return color == null ? UIManager.getColor("Label.foreground") : color;
	}

	private String displayPath(Path path) {
		Path root = rootSupplier.get();
		if (root == null) {
			return path.toString();
		}
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path normalizedPath = path.toAbsolutePath().normalize();
		if (normalizedPath.startsWith(normalizedRoot) && !normalizedPath.equals(normalizedRoot)) {
			return normalizedRoot.relativize(normalizedPath).toString();
		}
		return path.toString();
	}

	private static String preview(String lineText) {
		String trimmed = lineText == null ? "" : lineText.strip();
		if (trimmed.length() <= MAX_PREVIEW_LENGTH) {
			return trimmed;
		}
		return trimmed.substring(0, MAX_PREVIEW_LENGTH) + "...";
	}

	private final class SearchResultRenderer extends DefaultTreeCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
			if (value instanceof DefaultMutableTreeNode node) {
				Object userObject = node.getUserObject();
				if (userObject instanceof Path path) {
					setText(displayPath(path));
					setIcon(MaterialIcons.forFile(path));
					setFont(tree.getFont().deriveFont(Font.BOLD));
				}
				else if (userObject instanceof SearchMatch match) {
					setText(match.line() + ": " + preview(match.lineText()));
					setIcon(null);
					setFont(tree.getFont());
				}
				else {
					setText(userObject == null ? "" : userObject.toString());
				}
			}
			return this;
		}

	}

}
