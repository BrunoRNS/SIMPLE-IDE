package io.simpleide.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Searches text files below a workspace folder.
 *
 * <p>
 * The service walks the folder recursively, skips common build and version-control
 * directories, ignores binary files and files larger than {@link #MAX_FILE_BYTES}, and
 * returns one {@link SearchMatch} for every matching line. Matching is case-sensitive on
 * request and otherwise uses a locale-independent lower-case comparison. The method is
 * blocking and must run on a background thread.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class WorkspaceSearchService {

	private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceSearchService.class);

	/**
	 * Maximum number of matches returned by one search.
	 */
	public static final int DEFAULT_MAX_RESULTS = 500;

	private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;

	private static final int MAX_LINE_LENGTH = 4_000;

	private static final Set<String> SKIPPED_DIRECTORIES = Set.of(".git", ".hg", ".svn", ".idea", ".gradle",
			".simpleide", "node_modules", "target", "build", "out", "bin", "dist", "coverage");

	private static final Set<String> BINARY_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "ico", "pdf", "zip",
			"gz", "tar", "bz2", "xz", "7z", "jar", "class", "so", "dll", "dylib", "exe", "bin", "o", "a", "mp3", "mp4",
			"avi", "mov", "wav", "ogg", "woff", "woff2", "ttf", "otf", "eot", "db", "sqlite", "sqlite3");

	/**
	 * Searches a workspace for a text query.
	 * @param root workspace folder, or {@code null} when no folder is open
	 * @param query text to find
	 * @param matchCase whether the comparison is case-sensitive
	 * @param maxResults maximum number of matches
	 * @return matching lines ordered by file walk order
	 */
	public List<SearchMatch> search(Path root, String query, boolean matchCase, int maxResults) {
		if (root == null || query == null || query.isEmpty() || maxResults < 1 || !Files.isDirectory(root)) {
			return List.of();
		}
		String needle = matchCase ? query : query.toLowerCase(Locale.ROOT);
		List<SearchMatch> matches = new ArrayList<>();
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
					if (!directory.equals(root) && isSkippedDirectory(directory)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
					if (matches.size() >= maxResults) {
						return FileVisitResult.TERMINATE;
					}
					if (!attributes.isRegularFile() || attributes.size() > MAX_FILE_BYTES || isBinary(file)) {
						return FileVisitResult.CONTINUE;
					}
					searchFile(file, needle, matchCase, maxResults, matches);
					return matches.size() >= maxResults ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException error) {
					LOGGER.debug("Skipping unreadable workspace file {}.", file, error);
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException | RuntimeException error) {
			LOGGER.warn("Unable to search workspace {}.", root, error);
		}
		return List.copyOf(matches);
	}

	private void searchFile(Path file, String needle, boolean matchCase, int maxResults, List<SearchMatch> matches) {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (matches.size() >= maxResults) {
					return;
				}
				String searchable = line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line;
				String compared = matchCase ? searchable : searchable.toLowerCase(Locale.ROOT);
				int column = compared.indexOf(needle);
				if (column >= 0) {
					matches.add(new SearchMatch(file, lineNumber, column + 1, line));
				}
			}
		}
		catch (MalformedInputException error) {
			LOGGER.debug("Skipping binary or non-UTF-8 workspace file {}.", file);
		}
		catch (IOException | RuntimeException error) {
			LOGGER.debug("Skipping unreadable workspace file {}.", file, error);
		}
	}

	private boolean isSkippedDirectory(Path directory) {
		Path fileName = directory.getFileName();
		return fileName != null && SKIPPED_DIRECTORIES.contains(fileName.toString().toLowerCase(Locale.ROOT));
	}

	private boolean isBinary(Path file) {
		Path fileName = file.getFileName();
		if (fileName == null) {
			return false;
		}
		String name = fileName.toString();
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return false;
		}
		return BINARY_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
	}

	/**
	 * One matching line in the workspace.
	 *
	 * @param path file containing the match
	 * @param line one-based line number
	 * @param column one-based column of the first match in the line
	 * @param lineText full text of the matching line
	 */
	public record SearchMatch(Path path, int line, int column, String lineText) {
	}

}
