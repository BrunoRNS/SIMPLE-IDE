package io.simpleide.ui.sidebar;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
import javax.swing.SwingUtilities;

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
import io.simpleide.thread.nodes.edt.listeners.ActionNode;
import io.simpleide.thread.nodes.edt.listeners.MouseNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

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

	private long refreshGeneration;

	private long repositoryGeneration;

	/**
	 * Creates the Source Control panel.
	 */
	public GitPanel() {
		super(new BorderLayout());
		title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));

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
		statusList.addMouseListener(new MouseNode().onClick(event -> {
			if (event.getClickCount() == 1) {
				openSelectedFile();
			}
		}));
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
		switchBranch.addActionListener(new ActionNode(event -> switchBranch()));
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
		commit.addActionListener(new ActionNode(event -> commit()));
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
		configure.addActionListener(new ActionNode(event -> configureIdentity()));
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
		button.addActionListener(new ActionNode(event -> action.run()));
		panel.add(button);
		panel.add(Box.createHorizontalStrut(3));
	}

	/**
	 * Updates the panel for the folder currently opened in the Explorer.
	 * @param openedFolder Explorer folder, or {@code null}
	 */
	public void setOpenedFolder(Path openedFolder) {
		if (!SwingUtilities.isEventDispatchThread()) {
			ThreadMaster.getInstance().executeOnEDT(() -> setOpenedFolder(openedFolder));
			return;
		}
		this.openedFolder = openedFolder == null ? null : openedFolder.toAbsolutePath().normalize();
		long generation = ++repositoryGeneration;
		repository = null;
		if (this.openedFolder == null) {
			refresh();
			return;
		}
		repositoryLabel.setText("Finding Git repository...");
		setActionsEnabled(false);
		Path selectedFolder = this.openedFolder;
		ThreadMaster master = ThreadMaster.getInstance();
		master.register(new RunnableNode(() -> {
			Path found = gitService.resolveRepository(selectedFolder).orElse(null);
			master.executeOnEDT(() -> {
				if (generation != repositoryGeneration) {
					return;
				}
				repository = found;
				LOGGER.info("Source Control folder changed: repository={}.",
						repository == null ? "none" : repository.getFileName());
				refresh();
			});
		}));
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
	 * <p>
	 * Git commands run on the background pool so the window stays responsive even when a
	 * command is slow or fails while trying to prompt for input.
	 */
	public void refresh() {
		if (!SwingUtilities.isEventDispatchThread()) {
			ThreadMaster.getInstance().executeOnEDT(this::refresh);
			return;
		}
		Path currentRepository = repository;
		long generation = ++refreshGeneration;
		LOGGER.info("Source Control refresh started.");
		statusModel.clear();
		branchBox.removeAllItems();
		graph.setCommits(List.of());
		if (currentRepository == null) {
			repositoryLabel.setText(
					openedFolder == null ? "No folder opened" : "No Git repository for " + openedFolder.getFileName());
			setActionsEnabled(false);
			authorNameField.setText("");
			authorEmailField.setText("");
			LOGGER.info("Source Control refresh completed: no repository is bound.");
			return;
		}
		setActionsEnabled(true);
		repositoryLabel.setText("Repository: " + currentRepository.getFileName());
		ThreadMaster master = ThreadMaster.getInstance();
		try {
			master.register(new RunnableNode(() -> {
				String localName = gitService.localConfig(currentRepository, "user.name");
				String localEmail = gitService.localConfig(currentRepository, "user.email");
				String currentBranch = gitService.currentBranch(currentRepository);
				List<String> branches = gitService.branches(currentRepository);
				List<GitStatusEntry> status = gitService.status(currentRepository);
				List<GitCommit> commits = gitService.commits(currentRepository);
				master.executeOnEDT(() -> {
					if (generation != refreshGeneration || !Objects.equals(repository, currentRepository)) {
						LOGGER.debug("Discarding an outdated Source Control refresh result.");
						return;
					}
					authorNameField.setText(localName);
					authorEmailField.setText(localEmail);
					branchBox.removeAllItems();
					for (String branch : branches) {
						branchBox.addItem(branch);
					}
					branchBox.setSelectedItem(currentBranch);
					statusModel.clear();
					for (GitStatusEntry entry : status) {
						statusModel.addElement(entry);
					}
					graph.setCommits(commits);
					LOGGER.info("Source Control refresh completed: branch={}, changedFiles={}, commits={}.",
							currentBranch, status.size(), commits.size());
				});
			}));
		}
		catch (RuntimeException error) {
			LOGGER.warn("Unable to start the Source Control refresh for {}.", currentRepository.getFileName(), error);
			showFailure("Unable to refresh Source Control. Check the application log.", ErrorTypePopUp.INTERNAL_ERROR);
		}
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
		Path currentRepository = repository;
		Object selected = branchBox.getSelectedItem();
		if (currentRepository == null || selected == null) {
			return;
		}
		String branch = selected.toString();
		runOperation(currentRepository, "switch branch", () -> gitService.switchBranch(currentRepository, branch),
				null);
	}

	private void fetch() {
		Path currentRepository = repository;
		if (currentRepository != null) {
			runOperation(currentRepository, "fetch", () -> gitService.fetch(currentRepository), null);
		}
	}

	private void pull() {
		Path currentRepository = repository;
		if (currentRepository != null) {
			runOperation(currentRepository, "pull", () -> gitService.pull(currentRepository), null);
		}
	}

	private void push() {
		Path currentRepository = repository;
		if (currentRepository != null) {
			runOperation(currentRepository, "push", () -> gitService.push(currentRepository), null);
		}
	}

	private void stageSelected() {
		Path currentRepository = repository;
		GitStatusEntry selected = statusList.getSelectedValue();
		if (currentRepository != null && selected != null) {
			runOperation(currentRepository, "stage file", () -> gitService.stage(currentRepository, selected.path()),
					null);
		}
	}

	private void unstageSelected() {
		Path currentRepository = repository;
		GitStatusEntry selected = statusList.getSelectedValue();
		if (currentRepository != null && selected != null) {
			runOperation(currentRepository, "unstage file",
					() -> gitService.unstage(currentRepository, selected.path()), null);
		}
	}

	private void commit() {
		Path currentRepository = repository;
		if (currentRepository == null) {
			return;
		}
		String message = commitMessageField.getText();
		LOGGER.info("Source Control commit step requested for repository {}.", currentRepository.getFileName());
		runOperation(currentRepository, "commit", () -> gitService.commit(currentRepository, message),
				() -> commitMessageField.setText(""));
	}

	private void configureIdentity() {
		Path currentRepository = repository;
		if (currentRepository == null) {
			return;
		}
		String name = authorNameField.getText();
		String email = authorEmailField.getText();
		LOGGER.info("Source Control local identity configuration step requested for repository {}.",
				currentRepository.getFileName());
		runOperation(currentRepository, "configure local Git identity",
				() -> gitService.configureLocalIdentity(currentRepository, name, email), null);
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
		Path currentRepository = repository;
		if (currentRepository == null) {
			return;
		}
		runOperation(currentRepository, "checkout commit",
				() -> gitService.checkoutCommit(currentRepository, commit.hash()), null);
	}

	private void runOperation(Path currentRepository, String operation, Supplier<GitOperationResult> action,
			Runnable onSuccess) {
		ThreadMaster master = ThreadMaster.getInstance();
		try {
			master.register(new RunnableNode(() -> {
				GitOperationResult result;
				try {
					result = action.get();
				}
				catch (RuntimeException error) {
					LOGGER.error("Git operation {} failed unexpectedly for repository {}.", operation,
							currentRepository.getFileName(), error);
					master.executeOnEDT(() -> showFailure(
							"Git failed unexpectedly while trying to " + operation + ". Check the application log.",
							ErrorTypePopUp.INTERNAL_ERROR));
					return;
				}
				master.executeOnEDT(() -> {
					if (!Objects.equals(repository, currentRepository)) {
						LOGGER.debug("Discarding the Git {} result because the repository changed.", operation);
						return;
					}
					showResult(result, operation);
					if (result.success() && onSuccess != null) {
						onSuccess.run();
					}
					refresh();
				});
			}));
		}
		catch (RuntimeException error) {
			LOGGER.warn("Unable to start the Git {} operation for repository {}.", operation,
					currentRepository.getFileName(), error);
			showFailure("Unable to start Git while trying to " + operation + ". Check the application log.",
					ErrorTypePopUp.INTERNAL_ERROR);
		}
	}

	private void showResult(GitOperationResult result, String operation) {
		if (result.success()) {
			return;
		}
		String output = result.combinedOutput();
		String message;
		if (GitAuthMessage.isInteractionFailure(output)) {
			message = GitAuthMessage.interactionMessageFor(operation);
		}
		else if (GitAuthMessage.isAuthenticationFailure(output)) {
			message = GitAuthMessage.messageFor(operation);
		}
		else {
			message = output.isBlank() ? "Git failed while trying to " + operation + "." : output;
		}
		showFailure(message, ErrorTypePopUp.INTERNAL_ERROR);
	}

	private void showFailure(String message, ErrorTypePopUp type) {
		ThreadMaster.getInstance().executeOnEDT(() -> new ErrorPopUP(message, this, type).startManaged());
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
					case RENAMED -> "R";
					case COPIED -> "C";
				};
				label.setText(marker + "  " + entry.path().getFileName());
				label.setToolTipText(entry.path().toString());
			}
			return label;
		}

	}

}
