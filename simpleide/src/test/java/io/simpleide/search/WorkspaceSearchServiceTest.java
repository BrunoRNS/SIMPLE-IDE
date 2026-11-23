package io.simpleide.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.search.WorkspaceSearchService.SearchMatch;
import io.simpleide.search.WorkspaceSearchService.SearchOptions;

/**
 * Tests {@link io.simpleide.search.WorkspaceSearchService}.
 */
class WorkspaceSearchServiceTest {

	@TempDir
	Path tempDir;

	private final WorkspaceSearchService service = new WorkspaceSearchService();

	@Test
	void findsMatchingLinesAndSkipsIgnoredDirectories() throws IOException {
		Files.writeString(tempDir.resolve("alpha.txt"), "alpha first\nAlpha second\nthird\n");
		Files.createDirectories(tempDir.resolve("target"));
		Files.writeString(tempDir.resolve("target").resolve("ignored.txt"), "alpha\n");

		List<SearchMatch> matches = service.search(tempDir, "alpha", false, 10);

		assertThat(matches).hasSize(2);
		assertThat(matches).extracting(match -> match.path().getFileName().toString()).containsOnly("alpha.txt");
		assertThat(matches).extracting(SearchMatch::line).containsExactlyInAnyOrder(1, 2);
		assertThat(matches).allSatisfy(match -> assertThat(match.column()).isEqualTo(1));
	}

	@Test
	void matchCaseRestrictsResults() throws IOException {
		Files.writeString(tempDir.resolve("alpha.txt"), "alpha first\nAlpha second\nthird\n");

		List<SearchMatch> caseSensitive = service.search(tempDir, "alpha", true, 10);

		assertThat(caseSensitive).hasSize(1);
		assertThat(caseSensitive.get(0).line()).isEqualTo(1);
	}

	@Test
	void regularExpressionFindsEveryOccurrenceAndReportsItsColumn() throws IOException {
		Files.writeString(tempDir.resolve("matches.txt"), "alpha and alpha\nALPHA\n");
		SearchOptions options = new SearchOptions(false, true, true, false, Set.of(), "", "");

		List<SearchMatch> matches = service.search(tempDir, "alpha", options, 10);

		assertThat(matches).hasSize(3);
		assertThat(matches).extracting(SearchMatch::column).containsExactly(1, 11, 1);
		assertThat(matches).extracting(SearchMatch::line).containsExactly(1, 1, 2);
	}

	@Test
	void invalidRegularExpressionIsReported() throws IOException {
		Files.writeString(tempDir.resolve("alpha.txt"), "alpha\n");
		SearchOptions options = new SearchOptions(false, true, true, false, Set.of(), "", "");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.search(tempDir, "[", options, 10))
			.isInstanceOf(PatternSyntaxException.class);
	}

	@Test
	void usesGitIgnoreByDefaultAndCanSearchIgnoredFilesWhenDisabled() throws IOException {
		Files.writeString(tempDir.resolve(".gitignore"), "*.log\n!keep.log\nignored/\n");
		Files.writeString(tempDir.resolve("skip.log"), "needle\n");
		Files.writeString(tempDir.resolve("keep.log"), "needle\n");
		Files.createDirectories(tempDir.resolve("ignored"));
		Files.writeString(tempDir.resolve("ignored").resolve("hidden.txt"), "needle\n");
		Path nested = Files.createDirectories(tempDir.resolve("nested"));
		Files.writeString(nested.resolve(".gitignore"), "*.tmp\n");
		Files.writeString(nested.resolve("temporary.tmp"), "needle\n");

		List<SearchMatch> ignoredByDefault = service.search(tempDir, "needle", false, 10);
		SearchOptions includeIgnored = new SearchOptions(false, false, false, false, Set.of(), "", "");
		List<SearchMatch> withIgnored = service.search(tempDir, "needle", includeIgnored, 10);

		assertThat(ignoredByDefault).extracting(match -> match.path().getFileName().toString())
			.containsExactly("keep.log");
		assertThat(withIgnored).hasSize(4);
	}

	@Test
	void gitIgnorePatternsWithLeadingSlashOnlyMatchAtTheirBaseDirectory() throws IOException {
		Files.writeString(tempDir.resolve(".gitignore"), "/root-only.txt\n");
		Files.writeString(tempDir.resolve("root-only.txt"), "needle\n");
		Path nested = Files.createDirectories(tempDir.resolve("nested"));
		Files.writeString(nested.resolve("root-only.txt"), "needle\n");

		List<SearchMatch> matches = service.search(tempDir, "needle", false, 10);

		assertThat(matches).extracting(SearchMatch::path).containsExactly(nested.resolve("root-only.txt"));
	}

	@Test
	void includeAndExcludeGlobsFilterFiles() throws IOException {
		Path source = Files.createDirectories(tempDir.resolve("src"));
		Files.writeString(source.resolve("Alpha.java"), "needle\n");
		Files.writeString(source.resolve("Beta.java"), "needle\n");
		Files.writeString(tempDir.resolve("README.md"), "needle\n");
		SearchOptions options = new SearchOptions(false, false, true, false, Set.of(), "**/*.java", "**/Beta*");

		List<SearchMatch> matches = service.search(tempDir, "needle", options, 10);

		assertThat(matches).extracting(match -> match.path().getFileName().toString()).containsExactly("Alpha.java");
	}

	@Test
	void openEditorsOnlyRestrictsResultsToOpenPaths() throws IOException {
		Path openFile = tempDir.resolve("open.txt");
		Files.writeString(openFile, "needle\n");
		Files.writeString(tempDir.resolve("closed.txt"), "needle\n");
		SearchOptions options = new SearchOptions(false, false, true, true, Set.of(openFile), "", "");

		List<SearchMatch> matches = service.search(tempDir, "needle", options, 10);

		assertThat(matches).extracting(match -> match.path().getFileName().toString()).containsExactly("open.txt");
	}

	@Test
	void respectsMaxResults() throws IOException {
		Files.writeString(tempDir.resolve("many.txt"), "needle\nneedle\nneedle\n");

		List<SearchMatch> matches = service.search(tempDir, "needle", false, 2);

		assertThat(matches).hasSize(2);
	}

	@Test
	void ignoresMissingRootAndBlankQuery() {
		assertThat(service.search(null, "alpha", false, 10)).isEmpty();
		assertThat(service.search(tempDir, "", false, 10)).isEmpty();
		assertThat(service.search(tempDir, "alpha", false, 0)).isEmpty();
	}

}
