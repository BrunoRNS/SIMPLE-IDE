package io.simpleide.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import io.simpleide.git.GitStatusEntry.GitStatus;

/**
 * Executes Git operations for the repository associated with the opened folder.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class GitService {

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
		return GitRepositoryResolver.resolve(openedFolder);
	}

	/**
	 * Reads modified, deleted and added files.
	 * @param repository repository work tree
	 * @return status entries
	 */
	public List<GitStatusEntry> status(Path repository) {
		GitOperationResult result = run(repository, "status", "--porcelain=v1", "-z");
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
		return run(repository, "switch", branch);
	}

	/**
	 * Stages a file.
	 * @param repository repository work tree
	 * @param path repository-relative or absolute file path
	 * @return command result
	 */
	public GitOperationResult stage(Path repository, Path path) {
		return run(repository, "add", "--", repository.relativize(path.toAbsolutePath().normalize()).toString());
	}

	/**
	 * Unstages a file.
	 * @param repository repository work tree
	 * @param path repository-relative or absolute file path
	 * @return command result
	 */
	public GitOperationResult unstage(Path repository, Path path) {
		String relative = repository.relativize(path.toAbsolutePath().normalize()).toString();
		GitOperationResult restore = run(repository, "restore", "--staged", "--", relative);
		if (restore.success()) {
			return restore;
		}
		return run(repository, "rm", "--cached", "--", relative);
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
			Ref head = jgitRepository.findRef("HEAD");
			if (head != null) {
				walk.markStart(walk.parseCommit(head.getObjectId()));
			}
			PlotCommitList<PlotLane> plot = new PlotCommitList<>();
			plot.source(walk);
			plot.fillTo(200);

			List<GitCommit> commits = new ArrayList<>();
			for (var commit : plot) {
				PersonIdent author = commit.getAuthorIdent();
				List<String> parents = new ArrayList<>();
				for (var parent : commit.getParents()) {
					parents.add(parent.getName());
				}
				Instant date = author == null ? Instant.EPOCH
						: Instant.ofEpochSecond(author.getWhenAsInstant().getEpochSecond());
				String message = commit.getFullMessage();
				String shortHash = commit.getName().substring(0, 7);
				commits.add(new GitCommit(commit.getName(), shortHash, author == null ? "" : author.getName(),
						message == null ? "" : message.trim(), date, parents));
			}
			return List.copyOf(commits);
		}
		catch (IOException | IllegalArgumentException exception) {
			return List.of();
		}
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
	 * @param repository repository work tree
	 * @param arguments Git arguments
	 * @return command result
	 */
	public GitOperationResult run(Path repository, String... arguments) {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.add("-C");
		command.add(repository.toAbsolutePath().normalize().toString());
		command.addAll(List.of(arguments));
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
			String output;
			String error;
			try (BufferedReader outputReader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
					BufferedReader errorReader = new BufferedReader(
							new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				output = outputReader.lines().collect(java.util.stream.Collectors.joining("\n"));
				error = errorReader.lines().collect(java.util.stream.Collectors.joining("\n"));
			}
			int exitCode = process.waitFor();
			return new GitOperationResult(exitCode == 0, exitCode, output, error);
		}
		catch (IOException exception) {
			return new GitOperationResult(false, -1, "", exception.getMessage());
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return new GitOperationResult(false, -1, "", "Git command was interrupted.");
		}
	}

}
