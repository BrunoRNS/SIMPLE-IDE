package io.simpleide.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.search.WorkspaceSearchService;
import io.simpleide.search.WorkspaceSearchService.SearchMatch;
import io.simpleide.search.WorkspaceSearchService.SearchOptions;
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
 * The view searches the current workspace or open editor tabs, with regular-expression,
 * gitignore and glob options. Results are grouped by file, can be filtered in place, and
 * open at the selected occurrence when activated. Searching runs on a managed background
 * node and results are applied on the Swing Event Dispatch Thread.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class WorkspaceSearchPanel extends JPanel {

	private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceSearchPanel.class);

	private static final int MAX_PREVIEW_LENGTH = 120;

	private final Supplier<Path> rootSupplier;

	private final Supplier<Set<Path>> openFilesSupplier;

	private final BiConsumer<Path, Integer> openMatch;

	private final WorkspaceSearchService searchService = new WorkspaceSearchService();

	private final JTextField queryField = new JTextField(18);

	private final JCheckBox matchCase = new JCheckBox("Match case");

	private final JCheckBox regularExpression = new JCheckBox("Regex");

	private final JCheckBox openEditorsOnly = new JCheckBox("Open editors only");

	private final JCheckBox useGitIgnore = new JCheckBox("Use .gitignore", true);

	private final JTextField includeField = new JTextField();

	private final JTextField excludeField = new JTextField();

	private final JTextField resultFilterField = new JTextField();

	private final JLabel statusLabel = new JLabel(" ");

	private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Results");

	private final DefaultTreeModel model = new DefaultTreeModel(rootNode);

	private final JTree results = new JTree(model);

	private final JPanel body = new JPanel(new BorderLayout());

	private List<SearchMatch> allMatches = List.of();

	private List<SearchMatch> visibleMatches = List.of();

	private long searchGeneration;

	/**
	 * Creates the search view without an open-editor scope provider.
	 * @param rootSupplier supplies the folder currently open, or {@code null}
	 * @param openMatch opens a file at a one-based line number
	 */
	public WorkspaceSearchPanel(Supplier<Path> rootSupplier, BiConsumer<Path, Integer> openMatch) {
		this(rootSupplier, Set::of, openMatch);
	}

	/**
	 * Creates the search view.
	 * @param rootSupplier supplies the folder currently open, or {@code null}
	 * @param openFilesSupplier supplies the paths currently open in editor tabs
	 * @param openMatch opens a file at a one-based line number
	 */
	public WorkspaceSearchPanel(Supplier<Path> rootSupplier, Supplier<Set<Path>> openFilesSupplier,
			BiConsumer<Path, Integer> openMatch) {
		super(new BorderLayout());
		this.rootSupplier = Objects.requireNonNull(rootSupplier, "rootSupplier");
		this.openFilesSupplier = Objects.requireNonNull(openFilesSupplier, "openFilesSupplier");
		this.openMatch = Objects.requireNonNull(openMatch, "openMatch");
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
		JPanel header = new JPanel(new BorderLayout(0, 3));
		header.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
		JLabel title = new JLabel("Search");
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));
		JLabel subtitle = new JLabel("Find in files across your workspace");
		header.add(title, BorderLayout.NORTH);
		header.add(subtitle, BorderLayout.CENTER);
		add(header, BorderLayout.NORTH);
	}

	private void createSearchControls() {
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

		queryField.setName("workspaceSearchQuery");
		queryField.putClientProperty("JTextField.placeholderText", "Search");
		queryField.setToolTipText("Text or regular expression to find");
		queryField.getAccessibleContext().setAccessibleName("Search query");
		queryField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "simpleide.workspaceSearch");
		queryField.getActionMap()
			.put("simpleide.workspaceSearch", new SwingActionNode("Search workspace", this::startSearch));

		JButton searchButton = new JButton("Search");
		searchButton.setName("workspaceSearchButton");
		searchButton.setToolTipText("Search the current workspace");
		searchButton.addActionListener(new ActionNode(event -> startSearch()));
		JPanel queryRow = new JPanel(new BorderLayout(8, 0));
		queryRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		queryRow.add(queryField, BorderLayout.CENTER);
		queryRow.add(searchButton, BorderLayout.EAST);
		controls.add(queryRow);
		controls.add(Box.createVerticalStrut(8));

		JPanel options = new JPanel(new GridLayout(2, 2, 8, 3));
		options.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameOption(matchCase, "workspaceSearchMatchCase");
		nameOption(regularExpression, "workspaceSearchRegex");
		nameOption(openEditorsOnly, "workspaceSearchOpenEditorsOnly");
		nameOption(useGitIgnore, "workspaceSearchUseGitIgnore");
		regularExpression.setToolTipText("Treat the query as a regular expression");
		useGitIgnore.setToolTipText("Exclude files and folders marked by .gitignore");
		options.add(matchCase);
		options.add(regularExpression);
		options.add(openEditorsOnly);
		options.add(useGitIgnore);
		controls.add(options);
		controls.add(Box.createVerticalStrut(10));

		configureFileFilter(includeField, "workspaceSearchInclude", "e.g. *.java, src/**",
				"Comma-separated glob patterns to include");
		configureFileFilter(excludeField, "workspaceSearchExclude", "e.g. **/generated/**",
				"Comma-separated glob patterns to exclude");
		controls.add(labeledField("Files to include", includeField));
		controls.add(Box.createVerticalStrut(7));
		controls.add(labeledField("Files to exclude", excludeField));
		body.add(controls, BorderLayout.NORTH);
	}

	private void createResults() {
		JPanel resultPanel = new JPanel(new BorderLayout(0, 8));
		resultPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
		resultPanel.add(new JSeparator(), BorderLayout.NORTH);

		JPanel resultContent = new JPanel(new BorderLayout(0, 8));
		resultContent.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		JPanel heading = new JPanel(new BorderLayout(8, 0));
		JLabel title = new JLabel("Occurrences");
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		statusLabel.setName("workspaceSearchStatus");
		statusLabel.setHorizontalAlignment(JLabel.RIGHT);
		heading.add(title, BorderLayout.WEST);
		heading.add(statusLabel, BorderLayout.CENTER);
		JPanel resultHeader = new JPanel(new BorderLayout(0, 8));
		resultHeader.add(heading, BorderLayout.NORTH);

		resultFilterField.setName("workspaceSearchResultFilter");
		resultFilterField.putClientProperty("JTextField.placeholderText", "Filter by file or occurrence");
		resultFilterField.getAccessibleContext().setAccessibleName("Filter search occurrences");
		resultFilterField.setToolTipText("Filter the displayed file paths and matching lines");
		JButton clearFilter = new JButton("Clear");
		clearFilter.setToolTipText("Clear the occurrence filter");
		clearFilter.addActionListener(new ActionNode(event -> resultFilterField.setText("")));
		JPanel filterRow = new JPanel(new BorderLayout(8, 0));
		filterRow.add(resultFilterField, BorderLayout.CENTER);
		filterRow.add(clearFilter, BorderLayout.EAST);
		resultHeader.add(filterRow, BorderLayout.CENTER);

		results.setName("workspaceSearchResults");
		results.setRootVisible(false);
		results.setShowsRootHandles(true);
		results.setRowHeight(Math.max(22, results.getRowHeight()));
		results.setCellRenderer(new SearchResultRenderer());
		results.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		results.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "simpleide.openSearchMatch");
		results.getActionMap()
			.put("simpleide.openSearchMatch", new SwingActionNode("Open search result", this::openSelectedMatch));
		results.addMouseListener(new MouseNode().onClick(event -> {
			if (event.getClickCount() != 1) {
				return;
			}
			TreePath path = results.getPathForLocation(event.getX(), event.getY());
			openMatch(path);
		}));
		JScrollPane scrollPane = new JScrollPane(results);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		resultContent.add(resultHeader, BorderLayout.NORTH);
		resultContent.add(scrollPane, BorderLayout.CENTER);
		resultPanel.add(resultContent, BorderLayout.CENTER);
		body.add(resultPanel, BorderLayout.CENTER);

		resultFilterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				applyResultFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				applyResultFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				applyResultFilter();
			}
		});
	}

	private static void nameOption(JCheckBox option, String name) {
		option.setName(name);
		option.setFocusable(false);
		option.setOpaque(false);
	}

	private void configureFileFilter(JTextField field, String name, String placeholder, String toolTip) {
		field.setName(name);
		field.putClientProperty("JTextField.placeholderText", placeholder);
		field.setToolTipText(toolTip);
		field.getAccessibleContext().setAccessibleName(toolTip);
	}

	private JPanel labeledField(String labelText, JTextField field) {
		JPanel row = new JPanel(new BorderLayout(0, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel(labelText);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, Math.max(10f, label.getFont().getSize2D() - 1f)));
		row.add(label, BorderLayout.NORTH);
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	private void startSearch() {
		Path root = rootSupplier.get();
		String query = queryField.getText();
		if (root == null) {
			clearResults("Open a folder to search the workspace.");
			return;
		}
		if (query == null || query.isBlank()) {
			clearResults("Enter a search query.");
			return;
		}

		long generation = ++searchGeneration;
		allMatches = List.of();
		applyResultFilter();
		statusLabel.setText("Searching...");
		SearchOptions options = new SearchOptions(matchCase.isSelected(), regularExpression.isSelected(),
				useGitIgnore.isSelected(), openEditorsOnly.isSelected(), openFilesSupplier.get(),
				includeField.getText(), excludeField.getText());
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		try {
			threadMaster.register(new RunnableNode(() -> {
				try {
					List<SearchMatch> matches = searchService.search(root, query, options,
							WorkspaceSearchService.DEFAULT_MAX_RESULTS);
					threadMaster.executeOnEDT(() -> {
						if (generation == searchGeneration) {
							showResults(matches);
						}
					});
				}
				catch (java.util.regex.PatternSyntaxException error) {
					showSearchError(threadMaster, generation, "Invalid regular expression: " + error.getDescription());
				}
				catch (IllegalArgumentException error) {
					showSearchError(threadMaster, generation, "Invalid file filter: " + error.getMessage());
				}
				catch (RuntimeException error) {
					LOGGER.warn("Unable to search workspace {}.", root, error);
					threadMaster.executeOnEDT(() -> {
						if (generation == searchGeneration) {
							statusLabel.setText("Search failed. Check the application log.");
						}
					});
				}
			}));
		}
		catch (RuntimeException error) {
			LOGGER.warn("Unable to start workspace search in {}.", root, error);
			clearResults("Unable to start workspace search.");
		}
	}

	private void showSearchError(ThreadMaster threadMaster, long generation, String message) {
		LOGGER.info("Invalid workspace search input: {}.", message);
		threadMaster.executeOnEDT(() -> {
			if (generation == searchGeneration) {
				statusLabel.setText(message);
			}
		});
	}

	private void showResults(List<SearchMatch> matches) {
		allMatches = List.copyOf(matches);
		applyResultFilter();
	}

	private void applyResultFilter() {
		String filter = resultFilterField.getText().strip().toLowerCase(Locale.ROOT);
		Map<Path, List<SearchMatch>> files = new LinkedHashMap<>();
		for (SearchMatch match : allMatches) {
			String searchable = (displayPath(match.path()) + " " + match.line() + " " + match.column() + " "
					+ match.lineText())
				.toLowerCase(Locale.ROOT);
			if (filter.isEmpty() || searchable.contains(filter)) {
				files.computeIfAbsent(match.path(), ignored -> new ArrayList<>()).add(match);
			}
		}

		rootNode.removeAllChildren();
		visibleMatches = files.values().stream().flatMap(List::stream).toList();
		for (Map.Entry<Path, List<SearchMatch>> entry : files.entrySet()) {
			DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(entry.getKey());
			rootNode.add(fileNode);
			for (SearchMatch match : entry.getValue()) {
				fileNode.add(new DefaultMutableTreeNode(match));
			}
		}
		model.reload();
		for (int row = 0; row < results.getRowCount(); row++) {
			results.expandRow(row);
		}
		updateStatus(filter);
	}

	private void updateStatus(String filter) {
		if (allMatches.isEmpty()) {
			statusLabel.setText("No matches.");
			return;
		}
		if (visibleMatches.isEmpty()) {
			statusLabel.setText("No occurrences match the filter.");
			return;
		}
		long fileCount = visibleMatches.stream().map(SearchMatch::path).distinct().count();
		String count = filter.isEmpty() ? visibleMatches.size() + " matches"
				: visibleMatches.size() + " of " + allMatches.size() + " matches";
		if (visibleMatches.size() >= WorkspaceSearchService.DEFAULT_MAX_RESULTS) {
			count += "+";
		}
		statusLabel.setText(count + " in " + fileCount + (fileCount == 1 ? " file" : " files"));
	}

	private void clearResults(String message) {
		searchGeneration++;
		allMatches = List.of();
		visibleMatches = List.of();
		rootNode.removeAllChildren();
		model.reload();
		statusLabel.setText(message);
	}

	private void openSelectedMatch() {
		openMatch(results.getSelectionPath());
	}

	private void openMatch(TreePath treePath) {
		if (treePath == null || !(treePath.getLastPathComponent() instanceof DefaultMutableTreeNode node)) {
			return;
		}
		Object selected = node.getUserObject();
		if (selected instanceof SearchMatch match) {
			openMatch.accept(match.path(), match.line());
		}
		else if (selected instanceof Path path) {
			visibleMatches.stream()
				.filter(match -> match.path().equals(path))
				.findFirst()
				.ifPresent(match -> openMatch.accept(match.path(), match.line()));
		}
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
					setToolTipText(displayPath(path));
				}
				else if (userObject instanceof SearchMatch match) {
					setText(match.line() + ":" + match.column() + "  " + preview(match.lineText()));
					setIcon(null);
					setFont(tree.getFont());
					setToolTipText(match.path() + ":" + match.line());
				}
				else {
					setText(userObject == null ? "" : userObject.toString());
				}
			}
			return this;
		}

	}

}
