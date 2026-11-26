package io.simpleide.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.git.GitStatusEntry.GitStatus;
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.NodePattern;

/**
 * Executes Git operations for the repository associated with the opened folder.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class GitService {

	private static final Logger LOGGER = LoggerFactory.getLogger(GitService.class);

	private static final ManagedThreadPool PROCESS_POOL = ThreadMaster.getInstance().createThreadPool("git-process", 4);

	/**
	 * Creates a Git service.
	 */
	public GitService() {
	}

	/**
	 * Resolves the repository for a folder.
	 * @param openedFolder opened Explorer folder
	 * @return repository work tree
	 */
	public Optional<Path> resolveRepository(Path openedFolder) {
		LOGGER.info("Resolving Git repository for the Explorer folder.");
		Optional<Path> resolved = GitRepositoryResolver.resolve(openedFolder);
		LOGGER.info("Git repository resolution finished: {}.", resolved.isPresent() ? "repository found" : "not found");
		return resolved;
	}

	/**
	 * Reads modified, deleted and added files.
	 * @param repository repository work tree
	 * @return status entries
	 */
	public List<GitStatusEntry> status(Path repository) {
		GitOperationResult result = run(repository, "status", "--porcelain=v1", "-z", "--untracked-files=all",
				"--ignored=no");
		if (!result.success()) {
			return List.of();
		}
		return parseStatus(repository, result.output());
	}

	/**
	 * Maps porcelain status entries to display categories.
	 * @param repository repository work tree
	 * @param porcelain zero-separated porcelain status output
	 * @return mapped status entries
	 */
	public List<GitStatusEntry> parseStatus(Path repository, String porcelain) {
		if (porcelain == null || porcelain.isEmpty()) {
			return List.of();
		}
		List<GitStatusEntry> entries = new ArrayList<>();
		String[] records = porcelain.split("\0", -1);
		for (String record : records) {
			if (record.length() < 4) {
				continue;
			}
			String xy = record.substring(0, 2);
			String relative = record.substring(3);
			if (xy.equals("!!") || containsGitMetadata(relative)) {
				continue;
			}
			GitStatus status = mapStatus(xy);
			entries.add(new GitStatusEntry(repository.resolve(relative).normalize(), status));
		}
		return List.copyOf(entries);
	}

	/**
	 * Returns local and remote branch names.
	 * @param repository repository work tree
	 * @return branch names
	 */
	public List<String> branches(Path repository) {
		GitOperationResult result = run(repository, "branch", "--format=%(refname:short)");
		if (!result.success()) {
			return List.of();
		}
		return result.output().lines().map(String::trim).filter(line -> !line.isEmpty()).distinct().toList();
	}

	/**
	 * Reads the currently checked-out branch.
	 * @param repository repository work tree
	 * @return branch name, or {@code HEAD} for detached state
	 */
	public String currentBranch(Path repository) {
		GitOperationResult result = run(repository, "branch", "--show-current");
		if (!result.success()) {
			return "HEAD";
		}
		String branch = result.output().trim();
		return branch.isEmpty() ? "HEAD" : branch;
	}

	/**
	 * Switches to a local branch.
	 * @param repository repository work tree
	 * @param branch branch name
	 * @return command result
	 */
	public GitOperationResult switchBranch(Path repository, String branch) {
		return run(repository, "checkout", branch);
	}

	/**
	 * Stages a file.
	 * @param repository repository work tree
	 * @param path repository-relative or absolute file path
	 * @return command result
	 */
	public GitOperationResult stage(Path repository, Path path) {
		return run(repository, "add", "--", relativePath(repository, path));
	}

	private static String relativePath(Path repository, Path path) {
		Path normalizedRepository = repository.toAbsolutePath().normalize();
		Path normalizedPath = path.toAbsolutePath().normalize();
		if (!normalizedPath.startsWith(normalizedRepository)) {
			throw new IllegalArgumentException("Git file is outside the opened repository.");
		}
		return normalizedRepository.relativize(normalizedPath).toString();
	}

	/**
	 * Unstages a file.
	 * @param repository repository work tree
	 * @param path repository-relative or absolute file path
	 * @return command result
	 */
	public GitOperationResult unstage(Path repository, Path path) {
		String relative = relativePath(repository, path);
		GitOperationResult restore = run(repository, "reset", "-q", "HEAD", "--", relative);
		if (restore.success()) {
			return restore;
		}

		return run(repository, "rm", "--cached", "--", relative);
	}

	/**
	 * Stages all non-ignored changes and creates a commit.
	 * @param repository repository work tree
	 * @param message commit message
	 * @return command result
	 */
	public GitOperationResult commit(Path repository, String message) {
		if (message == null || message.isBlank()) {
			return new GitOperationResult(false, 1, "", "Commit message must not be blank.");
		}
		LOGGER.info("Git commit preparation step started: staging repository changes.");
		GitOperationResult stageResult = run(repository, "add", ".");
		if (!stageResult.success()) {
			LOGGER.warn("Git commit preparation step failed: unable to stage repository changes.");
			return stageResult;
		}
		LOGGER.info("Git commit preparation step completed: repository changes staged.");
		return run(repository, "commit", "-m", message.trim());
	}

	/**
	 * Returns a repository-local Git configuration value without consulting global
	 * config.
	 * @param repository repository work tree
	 * @param key local configuration key
	 * @return local value, or an empty string when unset
	 */
	public String localConfig(Path repository, String key) {
		GitOperationResult result = run(repository, "config", "--local", "--list");
		if (!result.success()) {
			return "";
		}
		String prefix = key + "=";
		return result.output()
			.lines()
			.filter(line -> line.startsWith(prefix))
			.map(line -> line.substring(prefix.length()).trim())
			.reduce((first, last) -> last)
			.orElse("");
	}

	/**
	 * Sets nonblank author identity values in this repository only.
	 * @param repository repository work tree
	 * @param name local author name, or blank to leave unchanged
	 * @param email local author email, or blank to leave unchanged
	 * @return command result
	 */
	public GitOperationResult configureLocalIdentity(Path repository, String name, String email) {
		if ((name == null || name.isBlank()) && (email == null || email.isBlank())) {
			LOGGER.info("Git identity step skipped: no local author values were provided.");
			return new GitOperationResult(true, 0, "", "");
		}
		if (name != null && !name.isBlank()) {
			GitOperationResult result = run(repository, "config", "--local", "user.name", name.trim());
			if (!result.success()) {
				return result;
			}
		}
		if (email != null && !email.isBlank()) {
			return run(repository, "config", "--local", "user.email", email.trim());
		}
		return new GitOperationResult(true, 0, "", "");
	}

	/**
	 * Fetches from configured remotes.
	 * @param repository repository work tree
	 * @return command result
	 */
	public GitOperationResult fetch(Path repository) {
		return run(repository, "fetch", "--all", "--prune");
	}

	/**
	 * Pulls the current branch.
	 * @param repository repository work tree
	 * @return command result
	 */
	public GitOperationResult pull(Path repository) {
		return run(repository, "pull", "--ff-only");
	}

	/**
	 * Pushes the current branch.
	 * @param repository repository work tree
	 * @return command result
	 */
	public GitOperationResult push(Path repository) {
		return run(repository, "push");
	}

	/**
	 * Checks out a previous commit, preserving a local working tree.
	 * @param repository repository work tree
	 * @param hash commit hash
	 * @return command result
	 */
	public GitOperationResult checkoutCommit(Path repository, String hash) {
		return run(repository, "checkout", "--detach", hash);
	}

	/**
	 * Reads the repository commit graph through JGit.
	 * @param repository repository work tree
	 * @return commit graph entries
	 */
	public List<GitCommit> commits(Path repository) {
		try (Repository jgitRepository = new FileRepositoryBuilder().setWorkTree(repository.toFile())
			.setMustExist(true)
			.build(); PlotWalk walk = new PlotWalk(jgitRepository)) {
			LOGGER.info("Commit graph step started for repository {}.", repository.getFileName());
			List<Ref> branchRefs = new ArrayList<>(jgitRepository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS));
			branchRefs.addAll(jgitRepository.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES));
			walk.addAdditionalRefs(branchRefs);
			for (Ref ref : branchRefs) {
				walk.markStart(walk.parseCommit(ref.getObjectId()));
			}
			Ref head = jgitRepository.findRef(Constants.HEAD);
			if (head != null && head.getObjectId() != null) {
				walk.markStart(walk.parseCommit(head.getObjectId()));
			}
			PlotCommitList<PlotLane> plot = new PlotCommitList<>();
			plot.source(walk);
			plot.fillTo(200);

			List<GitCommit> commits = new ArrayList<>();
			for (PlotCommit<PlotLane> commit : plot) {
				PersonIdent author = commit.getAuthorIdent();
				List<String> parents = new ArrayList<>();
				for (var parent : commit.getParents()) {
					parents.add(parent.getName());
				}
				List<String> refs = new ArrayList<>();
				for (int index = 0; index < commit.getRefCount(); index++) {
					refs.add(commit.getRef(index).getName());
				}
				Instant date = author == null ? Instant.EPOCH
						: Instant.ofEpochSecond(author.getWhenAsInstant().getEpochSecond());
				String message = commit.getFullMessage();
				String shortHash = commit.getName().substring(0, 7);
				commits.add(new GitCommit(commit.getName(), shortHash, author == null ? "" : author.getName(),
						message == null ? "" : message.trim(), date, parents,
						commit.getLane() == null ? 0 : commit.getLane().getPosition(), refs));
			}
			LOGGER.info("Commit graph step completed: {} commits loaded.", commits.size());
			return List.copyOf(commits);
		}
		catch (IOException | IllegalArgumentException exception) {
			LOGGER.error("Unable to read the commit graph for repository {}.", repository.getFileName(), exception);
			return List.of();
		}
	}

	private static boolean containsGitMetadata(String path) {
		for (Path part : Path.of(path)) {
			if (part.toString().equals(".git")) {
				return true;
			}
		}
		return false;
	}

	private static GitStatus mapStatus(String xy) {
		if (xy.contains("D")) {
			return GitStatus.DELETED;
		}
		if (xy.startsWith("??") || xy.startsWith("A") || xy.endsWith("A")) {
			return GitStatus.ADDED;
		}
		return GitStatus.MODIFIED;
	}

	/**
	 * Runs a Git command in exactly the repository work tree.
	 * <p>
	 * The command is always started non-interactively: standard input is closed
	 * immediately, terminal credential prompts are disabled and editors, pagers and SSH
	 * passphrase prompts are redirected so a Git operation can never wait for the user
	 * inside the application. Commands that still need input fail with an error instead.
	 * @param repository repository work tree
	 * @param arguments Git arguments
	 * @return command result
	 */
	public GitOperationResult run(Path repository, String... arguments) {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.add("--no-pager");
		command.add("-C");
		command.add(repository.toAbsolutePath().normalize().toString());
		command.addAll(List.of(arguments));
		String step = arguments.length == 0 ? "command" : arguments[0];
		LOGGER.info("Git step started: {} (repository={}).", step, repository.getFileName());
		CompletableFuture<GitOperationResult> result = new CompletableFuture<>();
		NodePattern task = PROCESS_POOL.execute(() -> {
			try {
				result.complete(runManagedProcess(repository, command, step));
			}
			catch (RuntimeException exception) {
				result.completeExceptionally(exception);
			}
		});
		try {
			return result.get();
		}
		catch (InterruptedException exception) {
			ThreadMaster.getInstance().removeNodeCascade(task);
			Thread.currentThread().interrupt();
			return new GitOperationResult(false, -1, "", "Git command was interrupted.");
		}
		catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			LOGGER.error("Git step {} failed unexpectedly for repository {}.", step, repository.getFileName(), cause);
			return new GitOperationResult(false, -1, "", cause == null ? exception.getMessage() : cause.getMessage());
		}
	}

	private static GitOperationResult runManagedProcess(Path repository, List<String> command, String step) {
		Process process = null;
		try {
			process = nonInteractiveProcess(command).start();
			closeStandardInput(process, step);
			String output;
			try (var stream = process.getInputStream()) {
				output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}
			int exitCode = process.waitFor();
			LOGGER.info("Git step completed: {} (exitCode={}).", step, exitCode);
			if (exitCode != 0) {
				LOGGER.warn("Git step failed: {} (repository={}, exitCode={}).", step, repository.getFileName(),
						exitCode);
			}
			return new GitOperationResult(exitCode == 0, exitCode, output, "");
		}
		catch (IOException exception) {
			LOGGER.error("Unable to start Git step {} for repository {}.", step, repository.getFileName(), exception);
			return new GitOperationResult(false, -1, "", exception.getMessage());
		}
		catch (InterruptedException exception) {
			if (process != null) {
				process.destroyForcibly();
			}
			Thread.currentThread().interrupt();
			return new GitOperationResult(false, -1, "", "Git command was interrupted.");
		}
	}

	private static ProcessBuilder nonInteractiveProcess(List<String> command) {
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
			.redirectInput(ProcessBuilder.Redirect.PIPE);
		Map<String, String> environment = builder.environment();
		environment.put("GIT_TERMINAL_PROMPT", "0");
		environment.put("GCM_INTERACTIVE", "never");
		environment.put("GIT_PAGER", "cat");
		environment.put("PAGER", "cat");
		environment.put("GIT_EDITOR", "true");
		environment.put("GIT_SEQUENCE_EDITOR", "true");
		environment.put("GIT_MERGE_AUTOEDIT", "no");
		environment.put("GIT_ASKPASS", "true");
		environment.put("SSH_ASKPASS", "true");
		environment.put("SSH_ASKPASS_REQUIRE", "never");
		String sshCommand = environment.get("GIT_SSH_COMMAND");
		if (sshCommand == null || sshCommand.isBlank()) {
			environment.put("GIT_SSH_COMMAND", "ssh -o BatchMode=yes");
		}
		else if (sshCommand.contains("ssh") && !sshCommand.contains("BatchMode")) {
			environment.put("GIT_SSH_COMMAND", sshCommand + " -o BatchMode=yes");
		}
		return builder;
	}

	private static void closeStandardInput(Process process, String step) {
		try {
			process.getOutputStream().close();
		}
		catch (IOException exception) {
			LOGGER.debug("Unable to close standard input for Git step {}.", step, exception);
		}
	}

}
