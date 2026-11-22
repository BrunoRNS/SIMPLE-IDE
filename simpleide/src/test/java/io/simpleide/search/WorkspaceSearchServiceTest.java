package io.simpleide.search;

import io.simpleide.search.WorkspaceSearchService.SearchMatch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

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
