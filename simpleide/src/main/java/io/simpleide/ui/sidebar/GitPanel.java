package io.simpleide.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.git.GitAuthMessage;
import io.simpleide.git.GitCommit;
import io.simpleide.git.GitOperationResult;
import io.simpleide.git.GitService;
import io.simpleide.git.GitStatusEntry;
import io.simpleide.thread.ThreadMaster;

/**
 * Source Control sidebar view bound to the Explorer folder.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class GitPanel extends JPanel {

	private static final Logger LOGGER = LoggerFactory.getLogger(GitPanel.class);

	private final GitService gitService = new GitService();

	private final JLabel title = new JLabel("SOURCE CONTROL");

	private final JLabel repositoryLabel = new JLabel("No folder opened");

	private final JComboBox<String> branchBox = new JComboBox<>();

	private final DefaultListModel<GitStatusEntry> statusModel = new DefaultListModel<>();

	private final JList<GitStatusEntry> statusList = new JList<>(statusModel);

	private final JTextField commitMessageField = new JTextField();

	private final JTextField authorNameField = new JTextField();

	private final JTextField authorEmailField = new JTextField();

	private final GitCommitGraphPanel graph = new GitCommitGraphPanel();

	private Consumer<Path> fileOpenListener = path -> {
	};

	private Path openedFolder;

	private Path repository;

	/**
	 * Creates the Source Control panel.
	 */
	public GitPanel() {
		super(new BorderLayout());
		title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 11f));

		JPanel top = createTopPanel();
		JScrollPane graphScroll = new JScrollPane(graph);
		graphScroll.setBorder(BorderFactory.createEmptyBorder());

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, graphScroll);
		split.setResizeWeight(0.42);
		split.setContinuousLayout(true);
		split.setBorder(BorderFactory.createEmptyBorder());
		add(title, BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);

		statusList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		statusList.setCellRenderer(new StatusRenderer());
		statusList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 1) {
					openSelectedFile();
				}
			}
		});
		graph.setCheckoutListener(this::checkoutCommit);
	}

	private JPanel createTopPanel() {
		JPanel top = new JPanel(new BorderLayout());
		top.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
		repositoryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		actions.add(repositoryLabel);
		actions.add(Box.createVerticalStrut(6));

		JPanel branchRow = new JPanel();
		branchRow.setLayout(new BoxLayout(branchRow, BoxLayout.X_AXIS));
		branchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		branchRow.add(branchBox);
		branchRow.add(Box.createHorizontalStrut(4));
		JButton switchBranch = new JButton("Switch");
		switchBranch.addActionListener(event -> switchBranch());
		branchRow.add(switchBranch);
		actions.add(branchRow);
		actions.add(Box.createVerticalStrut(4));

		JPanel gitActions = new JPanel();
		gitActions.setLayout(new BoxLayout(gitActions, BoxLayout.X_AXIS));
		gitActions.setAlignmentX(Component.LEFT_ALIGNMENT);
		addAction(gitActions, "Pull", this::pull);
		addAction(gitActions, "Fetch", this::fetch);
		addAction(gitActions, "Push", this::push);
		addAction(gitActions, "Stage", this::stageSelected);
		addAction(gitActions, "Unstage", this::unstageSelected);
		actions.add(gitActions);

		JPanel commitRow = new JPanel(new BorderLayout(4, 0));
		commitMessageField.setToolTipText("Commit message");
		commitMessageField.putClientProperty("JTextField.placeholderText", "Commit message");
		commitRow.add(commitMessageField, BorderLayout.CENTER);
		JButton commit = new JButton("Commit");
		commit.addActionListener(event -> commit());
		commitRow.add(commit, BorderLayout.EAST);
		commitRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		actions.add(Box.createVerticalStrut(4));
		actions.add(commitRow);

		JPanel identityRow = new JPanel(new BorderLayout(4, 0));
		authorNameField.setToolTipText("Local Git user.name (optional)");
		authorEmailField.setToolTipText("Local Git user.email (optional)");
		JPanel identityFields = new JPanel(new BorderLayout(4, 0));
		authorNameField.putClientProperty("JTextField.placeholderText", "user.name");
		authorEmailField.putClientProperty("JTextField.placeholderText", "user.email");
		identityFields.add(authorNameField, BorderLayout.NORTH);
		identityFields.add(authorEmailField, BorderLayout.SOUTH);
		identityRow.add(identityFields, BorderLayout.CENTER);
		JButton configure = new JButton("Set local identity");
		configure.addActionListener(event -> configureIdentity());
		identityRow.add(configure, BorderLayout.EAST);
		identityRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		actions.add(Box.createVerticalStrut(4));
		actions.add(identityRow);

		JScrollPane statusScroll = new JScrollPane(statusList);
		statusScroll.setPreferredSize(new Dimension(0, 150));
		top.add(actions, BorderLayout.NORTH);
		top.add(statusScroll, BorderLayout.CENTER);
		return top;
	}

	private void addAction(JPanel panel, String label, Runnable action) {
		JButton button = new JButton(label);
		button.addActionListener(event -> action.run());
		panel.add(button);
		panel.add(Box.createHorizontalStrut(3));
	}

	/**
	 * Updates the panel for the folder currently opened in the Explorer.
	 * @param openedFolder Explorer folder, or {@code null}
	 */
	public void setOpenedFolder(Path openedFolder) {
		this.openedFolder = openedFolder == null ? null : openedFolder.toAbsolutePath().normalize();
		this.repository = openedFolder == null ? null : gitService.resolveRepository(openedFolder).orElse(null);
		LOGGER.info("Source Control folder changed: repository={}.",
				repository == null ? "none" : repository.getFileName());
		refresh();
	}

	/**
	 * Registers the callback used to open a selected changed file in the editor.
	 * @param listener file-open callback
	 */
	public void setFileOpenListener(Consumer<Path> listener) {
		fileOpenListener = Objects.requireNonNull(listener, "listener");
	}

	/**
	 * Refreshes status, branch and graph for the bound repository.
	 */
	public void refresh() {
		LOGGER.info("Source Control refresh started.");
		statusModel.clear();
		branchBox.removeAllItems();
		graph.setCommits(List.of());
		if (repository == null) {
			repositoryLabel.setText(
					openedFolder == null ? "No folder opened" : "No Git repository for " + openedFolder.getFileName());
			setActionsEnabled(false);
			authorNameField.setText("");
			authorEmailField.setText("");
			LOGGER.info("Source Control refresh completed: no repository is bound.");
			return;
		}
		setActionsEnabled(true);
		repositoryLabel.setText("Repository: " + repository.getFileName());
		authorNameField.setText(gitService.localConfig(repository, "user.name"));
		authorEmailField.setText(gitService.localConfig(repository, "user.email"));
		String currentBranch = gitService.currentBranch(repository);
		for (String branch : gitService.branches(repository)) {
			branchBox.addItem(branch);
		}
		branchBox.setSelectedItem(currentBranch);

		List<GitStatusEntry> status = gitService.status(repository);
		for (GitStatusEntry entry : status) {
			statusModel.addElement(entry);
		}
		List<GitCommit> commits = gitService.commits(repository);
		graph.setCommits(commits);
		LOGGER.info("Source Control refresh completed: branch={}, changedFiles={}, commits={}.", currentBranch,
				status.size(), commits.size());
	}

	private void setActionsEnabled(boolean enabled) {
		setComponentEnabled(this, enabled);
		statusList.setEnabled(enabled);
	}

	private void setComponentEnabled(Component component, boolean enabled) {
		if (component instanceof JButton || component instanceof JComboBox || component instanceof JTextField) {
			component.setEnabled(enabled);
		}
		if (component instanceof java.awt.Container container) {
			for (Component child : container.getComponents()) {
				setComponentEnabled(child, enabled);
			}
		}
	}

	private void switchBranch() {
		if (repository == null || branchBox.getSelectedItem() == null) {
			return;
		}
		showResult(gitService.switchBranch(repository, branchBox.getSelectedItem().toString()), "switch branch");
		refresh();
	}

	private void fetch() {
		if (repository != null) {
			showResult(gitService.fetch(repository), "fetch");
		}
		refresh();
	}

	private void pull() {
		if (repository != null) {
			showResult(gitService.pull(repository), "pull");
		}
		refresh();
	}

	private void push() {
		if (repository != null) {
			showResult(gitService.push(repository), "push");
		}
		refresh();
	}

	private void stageSelected() {
		GitStatusEntry selected = statusList.getSelectedValue();
		if (repository != null && selected != null) {
			showResult(gitService.stage(repository, selected.path()), "stage file");
		}
		refresh();
	}

	private void unstageSelected() {
		GitStatusEntry selected = statusList.getSelectedValue();
		if (repository != null && selected != null) {
			showResult(gitService.unstage(repository, selected.path()), "unstage file");
		}
		refresh();
	}

	private void commit() {
		if (repository == null) {
			return;
		}
		String message = commitMessageField.getText();
		LOGGER.info("Source Control commit step requested for repository {}.", repository.getFileName());
		GitOperationResult result = gitService.commit(repository, message);
		showResult(result, "commit");
		if (result.success()) {
			commitMessageField.setText("");
		}
		refresh();
	}

	private void configureIdentity() {
		if (repository == null) {
			return;
		}
		LOGGER.info("Source Control local identity configuration step requested for repository {}.",
				repository.getFileName());
		GitOperationResult result = gitService.configureLocalIdentity(repository, authorNameField.getText(),
				authorEmailField.getText());
		showResult(result, "configure local Git identity");
		refresh();
	}

	private void openSelectedFile() {
		GitStatusEntry selected = statusList.getSelectedValue();
		if (selected == null) {
			return;
		}
		if (!java.nio.file.Files.isRegularFile(selected.path())) {
			LOGGER.debug("Changed file is not available in the working tree: {}.", selected.path().getFileName());
			return;
		}
		LOGGER.info("Opening changed file from Source Control: {}.", selected.path().getFileName());
		fileOpenListener.accept(selected.path());
	}

	private void checkoutCommit(GitCommit commit) {
		if (repository == null) {
			return;
		}
		showResult(gitService.checkoutCommit(repository, commit.hash()), "checkout commit");
		refresh();
	}

	private void showResult(GitOperationResult result, String operation) {
		if (result.success()) {
			return;
		}
		String output = result.combinedOutput();
		String message = GitAuthMessage.isAuthenticationFailure(output) ? GitAuthMessage.messageFor(operation)
				: output.isBlank() ? "Git failed while trying to " + operation + "." : output;
		ThreadMaster.getInstance()
			.executeOnEDT(() -> new ErrorPopUP(message, this, ErrorTypePopUp.INTERNAL_ERROR).startManaged());
	}

	private static final class StatusRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
				boolean focused) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
			if (value instanceof GitStatusEntry entry) {
				String marker = switch (entry.status()) {
					case ADDED -> "A";
					case MODIFIED -> "M";
					case DELETED -> "D";
				};
				label.setText(marker + "  " + entry.path().getFileName());
				label.setToolTipText(entry.path().toString());
			}
			return label;
		}

	}

}
