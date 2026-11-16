package io.simplebytecode.compiler.classloader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the fully qualified name of the public class declared in Java source.
 *
 * <p>
 * The detection is a lightweight regular-expression scan that ignores comments. It
 * supports an optional package declaration and common class modifiers such as
 * {@code abstract}, {@code final}, {@code sealed} and {@code non-sealed}.
 *
 * <p>
 * Covered by {@code JavaSourceClassNameTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class JavaSourceClassName {

	private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.$]+)\\s*;");

	private static final Pattern PUBLIC_CLASS = Pattern
		.compile("\\bpublic\\s+(?:(?:abstract|final|sealed|non-sealed)\\s+)*class\\s+([\\w$]+)");

	private JavaSourceClassName() {
	}

	/**
	 * Finds the first public class declared in the given source.
	 * @param source the Java source text to scan
	 * @return the package-qualified class name, or {@code null} when no public class is
	 * declared
	 */
	static String findMainClass(String source) {
		String withoutComments = source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
		Matcher classMatcher = PUBLIC_CLASS.matcher(withoutComments);
		if (!classMatcher.find()) {
			return null;
		}

		Matcher packageMatcher = PACKAGE.matcher(withoutComments);
		return packageMatcher.find() ? packageMatcher.group(1) + "." + classMatcher.group(1) : classMatcher.group(1);
	}

}
