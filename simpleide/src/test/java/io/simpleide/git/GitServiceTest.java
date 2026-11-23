package io.simpleide.git;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.git.GitStatusEntry.GitStatus;

/**
 * Integration tests for local Git operations.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
class GitServiceTest {

	@TempDir
	Path temp;

	private final GitService git = new GitService();

	@Test
	void resolvesNestedRepositoryAndSwitchesOpenedFolder() throws Exception {
		Path repo = temp.resolve("repo");
		Files.createDirectories(repo.resolve("src/nested"));
		init(repo);

		assertThat(GitRepositoryResolver.resolve(repo.resolve("src/nested"))).contains(repo);
		assertThat(GitRepositoryResolver.resolve(repo.resolve("src"))).contains(repo);

		Path outside = temp.resolve("outside/nested");
		Files.createDirectories(outside);
		assertThat(GitRepositoryResolver.resolve(outside)).isEmpty();
	}

	@Test
	void mapsAddedModifiedDeletedAndStatus() throws Exception {
		Path repo = temp.resolve("status");
		Files.createDirectories(repo);
		init(repo);
		Files.writeString(repo.resolve("tracked.txt"), "one");
		Files.writeString(repo.resolve("missing.txt"), "present");
		git.run(repo, "add", ".");
		git.run(repo, "commit", "-m", "base");

		Files.writeString(repo.resolve("tracked.txt"), "changed");
		Files.writeString(repo.resolve("new.txt"), "new");
		Files.delete(repo.resolve("missing.txt"));

		List<GitStatusEntry> entries = git.status(repo);
		assertThat(entries).anyMatch(entry -> entry.status() == GitStatus.MODIFIED
				&& entry.path().getFileName().toString().equals("tracked.txt"));
		assertThat(entries).anyMatch(
				entry -> entry.status() == GitStatus.ADDED && entry.path().getFileName().toString().equals("new.txt"));
		assertThat(entries).anyMatch(entry -> entry.status() == GitStatus.DELETED
				&& entry.path().getFileName().toString().equals("missing.txt"));
	}

	@Test
	void parsesStatusCategories() {
		Path repo = temp.resolve("repo");
		List<GitStatusEntry> entries = git.parseStatus(repo,
				"M  modified.txt\0D  deleted.txt\0?? added.txt\0A  staged.txt\0");
		assertThat(entries).extracting(GitStatusEntry::status)
			.containsExactly(GitStatus.MODIFIED, GitStatus.DELETED, GitStatus.ADDED, GitStatus.ADDED);
	}

	@Test
	void stagesAndUnstagesAFile() throws Exception {
		Path repo = temp.resolve("stage");
		Files.createDirectories(repo);
		init(repo);
		Path file = repo.resolve("file.txt");
		Files.writeString(file, "content");

		assertThat(git.stage(repo, file).success()).isTrue();
		assertThat(git.status(repo)).anyMatch(entry -> entry.status() == GitStatus.ADDED);
		assertThat(git.unstage(repo, file).success()).isTrue();
		assertThat(git.status(repo)).anyMatch(entry -> entry.status() == GitStatus.ADDED);
	}

	@Test
	void createsBranchesMergesAndReadsCommitGraph() throws Exception {
		Path repo = temp.resolve("graph");
		Files.createDirectories(repo);
		init(repo);
		Files.writeString(repo.resolve("file.txt"), "main");
		git.run(repo, "add", ".");
		git.run(repo, "commit", "-m", "initial");
		git.run(repo, "switch", "-c", "feature");
		Files.writeString(repo.resolve("file.txt"), "feature");
		git.run(repo, "commit", "-am", "feature commit");
		git.run(repo, "switch", "master");
		Files.writeString(repo.resolve("main.txt"), "main");
		git.run(repo, "add", ".");
		git.run(repo, "commit", "-m", "main commit");
		git.run(repo, "merge", "--no-edit", "feature");

		assertThat(git.branches(repo)).contains("feature", "master");
		List<GitCommit> commits = git.commits(repo);
		assertThat(commits).isNotEmpty();
		assertThat(commits).anyMatch(commit -> commit.parents().size() >= 2);
	}

	@Test
	void fetchPullAndPushAgainstLocalBareRepository() throws Exception {
		Path bare = temp.resolve("remote.git");
		run(temp, "git", "init", "--bare", bare.toString());
		Path repo = temp.resolve("push");
		Files.createDirectories(repo);
		init(repo);
		Files.writeString(repo.resolve("file.txt"), "first");
		git.run(repo, "add", ".");
		git.run(repo, "commit", "-m", "first");
		git.run(repo, "remote", "add", "origin", bare.toString());
		git.run(repo, "push", "-u", "origin", "master");
		assertThat(git.push(repo).success()).isTrue();

		Path clone = temp.resolve("clone");
		run(temp, "git", "clone", bare.toString(), clone.toString());
		Files.writeString(clone.resolve("file.txt"), "second");
		run(clone, "git", "add", ".");
		run(clone, "git", "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "second");
		run(clone, "git", "push", "origin", "HEAD");

		assertThat(git.fetch(repo).success()).isTrue();
		assertThat(git.pull(repo).success()).isTrue();
	}

	@Test
	void authenticationMessageExplainsGhAndSshWithoutLogin() {
		assertThat(GitAuthMessage.isAuthenticationFailure("fatal: authentication failed")).isTrue();
		String message = GitAuthMessage.messageFor("push");
		assertThat(message).contains("gh auth login", "gh auth setup-git", "SSH", "does not perform login");
	}

	private void init(Path repo) throws Exception {
		Files.createDirectories(repo);
		run(repo, "git", "init", "-b", "master");
		run(repo, "git", "config", "user.name", "Test");
		run(repo, "git", "config", "user.email", "test@example.com");
	}

	private static void run(Path directory, String... command) throws Exception {
		Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
		String output;
		try (var reader = new java.io.BufferedReader(
				new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
		}
		int code = process.waitFor();
		assertThat(code).as("command %s failed: %s", List.of(command), output).isZero();
	}

}
