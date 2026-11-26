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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Searches text files below a workspace folder.
 *
 * <p>
 * The service walks the folder recursively, skips common build and version-control
 * directories, supports gitignore and glob filters, ignores binary files and files larger
 * than {@link #MAX_FILE_BYTES}, and returns one {@link SearchMatch} for every occurrence.
 * The method is blocking and must run on a background thread.
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
	 * Searches a workspace for a literal text query.
	 * @param root workspace folder, or {@code null} when no folder is open
	 * @param query text to find
	 * @param matchCase whether the comparison is case-sensitive
	 * @param maxResults maximum number of matches
	 * @return matching occurrences ordered by file path, line and column
	 */
	public List<SearchMatch> search(Path root, String query, boolean matchCase, int maxResults) {
		return search(root, query, new SearchOptions(matchCase, false, true, false, Set.of(), "", ""), maxResults);
	}

	/**
	 * Searches a workspace with the requested scope and matching options.
	 * @param root workspace folder, or {@code null} when no folder is open
	 * @param query text or regular expression to find
	 * @param options query, scope and file filter settings
	 * @param maxResults maximum number of occurrences
	 * @return matching occurrences ordered by file path, line and column
	 * @throws PatternSyntaxException if the regular expression is invalid
	 * @throws IllegalArgumentException if a file glob is invalid
	 */
	public List<SearchMatch> search(Path root, String query, SearchOptions options, int maxResults) {
		if (root == null || query == null || query.isEmpty() || options == null || maxResults < 1
				|| !Files.isDirectory(root)) {
			return List.of();
		}

		Path normalizedRoot = root.toAbsolutePath().normalize();
		Pattern queryPattern = compileQuery(query, options);
		FileGlobFilter includes = FileGlobFilter.compile(options.filesToInclude());
		FileGlobFilter excludes = FileGlobFilter.compile(options.filesToExclude());
		List<Path> openedFiles = options.openFiles().stream().map(path -> path.toAbsolutePath().normalize()).toList();
		List<SearchMatch> matches = new ArrayList<>();
		List<IgnoreRule> ignoreRules = new ArrayList<>();
		Deque<Integer> ruleCounts = new ArrayDeque<>();
		try {
			Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
					if (!directory.equals(normalizedRoot) && isSkippedDirectory(directory)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					if (options.useGitIgnore() && !directory.equals(normalizedRoot)
							&& isIgnored(normalizedRoot.relativize(directory), true, ignoreRules)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					ruleCounts.push(ignoreRules.size());
					if (options.useGitIgnore()) {
						loadIgnoreRules(directory, normalizedRoot, ignoreRules);
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
					Path relative = normalizedRoot.relativize(file);
					if (options.openEditorsOnly() && !openedFiles.contains(file.toAbsolutePath().normalize())) {
						return FileVisitResult.CONTINUE;
					}
					if (options.useGitIgnore() && isIgnored(relative, false, ignoreRules)) {
						return FileVisitResult.CONTINUE;
					}
					if ((!includes.patterns().isEmpty() && !includes.matches(relative)) || excludes.matches(relative)) {
						return FileVisitResult.CONTINUE;
					}
					searchFile(file, queryPattern, maxResults, matches);
					return matches.size() >= maxResults ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path directory, IOException error) {
					int ruleCount = ruleCounts.pop();
					while (ignoreRules.size() > ruleCount) {
						ignoreRules.remove(ignoreRules.size() - 1);
					}
					if (error != null) {
						LOGGER.debug("Unable to read workspace directory {}.", directory, error);
					}
					return FileVisitResult.CONTINUE;
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
		matches.sort(Comparator
			.comparing((SearchMatch match) -> normalizePath(normalizedRoot.relativize(match.path()).toString()))
			.thenComparingInt(SearchMatch::line)
			.thenComparingInt(SearchMatch::column));
		return List.copyOf(matches);
	}

	private Pattern compileQuery(String query, SearchOptions options) {
		String expression = options.regularExpression() ? query : Pattern.quote(query);
		int flags = options.matchCase() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		return Pattern.compile(expression, flags);
	}

	private void loadIgnoreRules(Path directory, Path root, List<IgnoreRule> rules) {
		Path ignoreFile = directory.resolve(".gitignore");
		if (!Files.isRegularFile(ignoreFile)) {
			return;
		}
		String base = normalizePath(root.relativize(directory).toString());
		try {
			for (String line : Files.readAllLines(ignoreFile, StandardCharsets.UTF_8)) {
				IgnoreRule.parse(line, base).ifPresent(rules::add);
			}
		}
		catch (IOException error) {
			LOGGER.debug("Unable to read ignore file {}.", ignoreFile, error);
		}
	}

	private boolean isIgnored(Path relativePath, boolean directory, List<IgnoreRule> rules) {
		String[] components = normalizePath(relativePath.toString()).split("/");
		StringBuilder current = new StringBuilder();
		for (int index = 0; index < components.length; index++) {
			if (current.length() > 0) {
				current.append('/');
			}
			current.append(components[index]);
			boolean isDirectory = index < components.length - 1 || directory;
			boolean ignored = false;
			for (IgnoreRule rule : rules) {
				if (rule.matches(current.toString(), isDirectory)) {
					ignored = !rule.negated();
				}
			}
			if (ignored) {
				return true;
			}
		}
		return false;
	}

	private void searchFile(Path file, Pattern pattern, int maxResults, List<SearchMatch> matches) {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (matches.size() >= maxResults) {
					return;
				}
				String searchable = line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) : line;
				Matcher matcher = pattern.matcher(searchable);
				while (matcher.find() && matches.size() < maxResults) {
					matches.add(new SearchMatch(file, lineNumber, matcher.start() + 1, line));
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

	private static String normalizePath(String path) {
		return path.replace('\\', '/');
	}

	/**
	 * Search query behavior and scope.
	 *
	 * @param matchCase whether matches respect letter case
	 * @param regularExpression whether the query is a Java regular expression
	 * @param useGitIgnore whether files ignored by applicable {@code .gitignore} files
	 * are excluded
	 * @param openEditorsOnly whether the search is limited to {@code openFiles}
	 * @param openFiles paths currently open in editor tabs
	 * @param filesToInclude comma-separated include globs
	 * @param filesToExclude comma-separated exclude globs
	 */
	public record SearchOptions(boolean matchCase, boolean regularExpression, boolean useGitIgnore,
			boolean openEditorsOnly, Set<Path> openFiles, String filesToInclude, String filesToExclude) {

		/** Creates an immutable, normalized options snapshot. */
		public SearchOptions {
			Set<Path> normalized = new HashSet<>();
			if (openFiles != null) {
				for (Path path : openFiles) {
					if (path != null) {
						normalized.add(path.toAbsolutePath().normalize());
					}
				}
			}
			openFiles = Set.copyOf(normalized);
			filesToInclude = filesToInclude == null ? "" : filesToInclude;
			filesToExclude = filesToExclude == null ? "" : filesToExclude;
		}

	}

	/**
	 * One matching occurrence in the workspace.
	 *
	 * @param path file containing the match
	 * @param line one-based line number
	 * @param column one-based column of the match
	 * @param lineText full text of the matching line
	 */
	public record SearchMatch(Path path, int line, int column, String lineText) {
	}

	private record IgnoreRule(String base, Pattern pattern, boolean pathPattern, boolean directoryOnly,
			boolean negated) {

		private static Optional<IgnoreRule> parse(String source, String base) {
			String line = trimTrailingSpaces(source);
			if (line.isBlank() || line.startsWith("#")) {
				return Optional.empty();
			}
			boolean escapedLeadingMarker = line.startsWith("\\#") || line.startsWith("\\!");
			if (escapedLeadingMarker) {
				line = line.substring(1);
			}
			boolean negated = !escapedLeadingMarker && line.startsWith("!");
			if (negated) {
				line = line.substring(1);
			}
			boolean anchored = line.startsWith("/");
			if (anchored) {
				line = line.substring(1);
			}
			boolean directoryOnly = line.endsWith("/") && !line.endsWith("\\/");
			if (directoryOnly) {
				line = line.substring(0, line.length() - 1);
			}
			if (line.isEmpty()) {
				return Optional.empty();
			}
			boolean pathPattern = anchored || line.contains("/");
			try {
				return Optional.of(new IgnoreRule(base, FileGlobFilter.compileGlob(line, true), pathPattern,
						directoryOnly, negated));
			}
			catch (IllegalArgumentException error) {
				LOGGER.warn("Skipping invalid .gitignore pattern '{}'.", source, error);
				return Optional.empty();
			}
		}

		private boolean matches(String path, boolean directory) {
			String localPath;
			if (base.isEmpty()) {
				localPath = path;
			}
			else if (path.startsWith(base + "/")) {
				localPath = path.substring(base.length() + 1);
			}
			else {
				return false;
			}
			if (localPath.isEmpty()) {
				return false;
			}
			if (pathPattern) {
				return (!directoryOnly || directory) && pattern.matcher(localPath).matches();
			}
			String[] components = localPath.split("/");
			for (int index = 0; index < components.length; index++) {
				boolean componentDirectory = index < components.length - 1 || directory;
				if ((!directoryOnly || componentDirectory) && pattern.matcher(components[index]).matches()) {
					return true;
				}
			}
			return false;
		}

		private static String trimTrailingSpaces(String line) {
			int end = line.length();
			while (end > 0 && line.charAt(end - 1) == ' ') {
				int escapeCount = 0;
				for (int index = end - 2; index >= 0 && line.charAt(index) == '\\'; index--) {
					escapeCount++;
				}
				if (escapeCount % 2 == 1) {
					break;
				}
				end--;
			}
			return line.substring(0, end);
		}

	}

}
