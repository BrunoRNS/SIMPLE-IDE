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
		Files.writeString(repo.resolve(".gitignore"), "*.secret\n");
		Files.writeString(repo.resolve("tracked.txt"), "one");
		Files.writeString(repo.resolve("missing.txt"), "present");
		git.run(repo, "add", ".");
		git.run(repo, "commit", "-m", "base");

		Files.writeString(repo.resolve("tracked.txt"), "changed");
		Files.writeString(repo.resolve("new.txt"), "new");
		Files.writeString(repo.resolve("ignored.secret"), "ignored");
		Files.delete(repo.resolve("missing.txt"));

		List<GitStatusEntry> entries = git.status(repo);
		assertThat(entries).anyMatch(entry -> entry.status() == GitStatus.MODIFIED
				&& entry.path().getFileName().toString().equals("tracked.txt"));
		assertThat(entries).anyMatch(
				entry -> entry.status() == GitStatus.ADDED && entry.path().getFileName().toString().equals("new.txt"));
		assertThat(entries).anyMatch(entry -> entry.status() == GitStatus.DELETED
				&& entry.path().getFileName().toString().equals("missing.txt"));
		assertThat(entries).noneMatch(entry -> entry.path().toString().contains(".git"));
		assertThat(entries).noneMatch(entry -> entry.path().getFileName().toString().equals("ignored.secret"));
	}

	@Test
	void parsesStatusCategories() {
		Path repo = temp.resolve("repo");
		List<GitStatusEntry> entries = git.parseStatus(repo,
				"M  modified.txt\0D  deleted.txt\0?? added.txt\0A  staged.txt\0?? .git/config\0!! ignored.secret\0");
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
		git.run(repo, "checkout", "-b", "feature");
		Files.writeString(repo.resolve("file.txt"), "feature");
		git.run(repo, "commit", "-am", "feature commit");
		git.run(repo, "checkout", "master");
		Files.writeString(repo.resolve("main.txt"), "main");
		git.run(repo, "add", ".");
		git.run(repo, "commit", "-m", "main commit");
		git.run(repo, "merge", "--no-edit", "feature");

		assertThat(git.branches(repo)).contains("feature", "master");
		List<GitCommit> commits = git.commits(repo);
		assertThat(commits).isNotEmpty();
		assertThat(commits).anyMatch(commit -> commit.parents().size() >= 2);
		assertThat(commits).anyMatch(commit -> commit.lane() > 0);
		assertThat(commits).anyMatch(commit -> commit.refs().contains("refs/heads/feature"));
		assertThat(commits).anyMatch(commit -> commit.refs().contains("refs/heads/master"));
	}

	@Test
	void commitsAndStoresOnlyNonblankIdentityLocally() throws Exception {
		Path repo = temp.resolve("identity");
		Files.createDirectories(repo);
		init(repo);
		Files.writeString(repo.resolve("file.txt"), "content");

		assertThat(git.configureLocalIdentity(repo, "Local Author", "local@example.com").success()).isTrue();
		assertThat(git.localConfig(repo, "user.name")).isEqualTo("Local Author");
		assertThat(git.localConfig(repo, "user.email")).isEqualTo("local@example.com");
		assertThat(git.commit(repo, "commit message").success()).isTrue();
		assertThat(git.run(repo, "status", "--porcelain").output()).isBlank();
		assertThat(git.run(repo, "log", "-1", "--format=%s").output().trim()).isEqualTo("commit message");
		assertThat(git.commit(repo, " ").success()).isFalse();
		assertThat(git.configureLocalIdentity(repo, " ", " ").success()).isTrue();
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

	@Test
	void interactionMessageExplainsDisabledPrompts() {
		assertThat(GitAuthMessage.isInteractionFailure("fatal: could not read Username: terminal prompts disabled"))
			.isTrue();
		assertThat(GitAuthMessage.isInteractionFailure("Host key verification failed.")).isTrue();
		assertThat(GitAuthMessage.isInteractionFailure("Already up to date.")).isFalse();
		String message = GitAuthMessage.interactionMessageFor("push");
		assertThat(message).contains("stopped instead of waiting for input", "gh auth login", "gh auth setup-git");
	}

	@Test
	void runsCommandsWithClosedStandardInputAndNonInteractiveEnvironment() throws Exception {
		Path repo = temp.resolve("non-interactive");
		Files.createDirectories(repo);
		init(repo);
		assertThat(git.run(repo, "config", "alias.waitsforinput", "!read answer; echo finished").success()).isTrue();

		GitOperationResult result = git.run(repo, "waitsforinput");

		assertThat(result.success()).isTrue();
		assertThat(result.output()).contains("finished");
		assertThat(git.run(repo, "config", "alias.showenvironment", "!echo $GIT_TERMINAL_PROMPT; echo $GIT_SSH_COMMAND")
			.success()).isTrue();
		GitOperationResult environment = git.run(repo, "showenvironment");
		assertThat(environment.output()).contains("0", "BatchMode=yes");
	}

	private void init(Path repo) throws Exception {
		Files.createDirectories(repo);
		run(repo, "git", "init");
		run(repo, "git", "symbolic-ref", "HEAD", "refs/heads/master");
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
