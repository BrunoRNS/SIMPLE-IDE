package io.simpleide.search;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compiles and applies include, exclude, and ignore glob patterns. */
/**
 * @author BrunoRNS
 * @since v1.1.0
 */
record FileGlobFilter(List<GlobPattern> patterns) {

	static FileGlobFilter compile(String source) {
		List<GlobPattern> patterns = splitGlobs(source).stream().map(GlobPattern::compile).toList();
		return new FileGlobFilter(patterns);
	}

	boolean matches(Path relativePath) {
		String normalizedPath = normalizePath(relativePath.toString());
		String fileName = normalizePath(relativePath.getFileName().toString());
		return patterns.stream()
			.anyMatch(glob -> glob.pattern().matcher(glob.pathPattern() ? normalizedPath : fileName).matches());
	}

	private static List<String> splitGlobs(String source) {
		List<String> globs = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int braceDepth = 0;
		for (int index = 0; index < source.length(); index++) {
			char character = source.charAt(index);
			if (character == '{') {
				braceDepth++;
			}
			else if (character == '}') {
				braceDepth = Math.max(0, braceDepth - 1);
			}
			if ((character == ',' || character == ';') && braceDepth == 0) {
				addGlob(globs, current);
			}
			else {
				current.append(character);
			}
		}
		addGlob(globs, current);
		return globs;
	}

	private static void addGlob(List<String> globs, StringBuilder current) {
		String glob = current.toString().trim();
		if (!glob.isEmpty()) {
			globs.add(glob);
		}
		current.setLength(0);
	}

	private record GlobPattern(Pattern pattern, boolean pathPattern) {

		private static GlobPattern compile(String source) {
			String glob = normalizePath(source.trim());
			while (glob.startsWith("./")) {
				glob = glob.substring(2);
			}
			if (glob.startsWith("/")) {
				glob = glob.substring(1);
			}
			if (glob.isEmpty()) {
				throw new IllegalArgumentException("File filter patterns cannot be empty.");
			}
			return new GlobPattern(compileGlob(glob, false), glob.contains("/"));
		}

	}

	static Pattern compileGlob(String glob, boolean ignorePattern) {
		StringBuilder regex = new StringBuilder("^");
		boolean inGroup = false;
		for (int index = 0; index < glob.length(); index++) {
			char character = glob.charAt(index);
			if (character == '\\' && index + 1 < glob.length()) {
				appendLiteral(regex, glob.charAt(++index));
			}
			else if (character == '*') {
				if (index + 1 < glob.length() && glob.charAt(index + 1) == '*') {
					index++;
					if (index + 1 < glob.length() && glob.charAt(index + 1) == '/') {
						index++;
						regex.append("(?:.*/)?");
					}
					else {
						regex.append(".*");
					}
				}
				else {
					regex.append("[^/]*");
				}
			}
			else if (character == '?') {
				regex.append("[^/]");
			}
			else if (character == '[') {
				int closing = glob.indexOf(']', index + 1);
				if (closing < 0) {
					appendLiteral(regex, character);
				}
				else {
					regex.append('[');
					int classStart = index + 1;
					if (classStart < closing && glob.charAt(classStart) == '!') {
						regex.append('^');
						classStart++;
					}
					regex.append(glob, classStart, closing + 1);
					index = closing;
				}
			}
			else if (!ignorePattern && character == '{') {
				regex.append("(?:");
				inGroup = true;
			}
			else if (!ignorePattern && character == '}' && inGroup) {
				regex.append(')');
				inGroup = false;
			}
			else if (!ignorePattern && character == ',' && inGroup) {
				regex.append('|');
			}
			else {
				appendLiteral(regex, character);
			}
		}
		if (inGroup) {
			throw new IllegalArgumentException("Unclosed group in file filter: " + glob);
		}
		try {
			return Pattern.compile(regex.append('$').toString());
		}
		catch (PatternSyntaxException error) {
			throw new IllegalArgumentException("Invalid file filter: " + glob, error);
		}
	}

	private static void appendLiteral(StringBuilder regex, char character) {
		if ("\\.[]{}()+-^$|".indexOf(character) >= 0) {
			regex.append('\\');
		}
		regex.append(character);
	}

	private static String normalizePath(String path) {
		return path.replace('\\', '/');
	}

}
