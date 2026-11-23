package io.simplebytecode.compiler.utils;

import java.util.Locale;

import io.simplebytecode.compiler.RunnerInterface;
import io.simplebytecode.compiler.classloader.JavaRunner;
import io.simplebytecode.compiler.groovy.GroovyRunner;
import io.simplebytecode.compiler.kotlin.KotlinRunner;

/**
 * Maps source file extensions to runner implementations.
 *
 * <p>
 * The lookup is case-insensitive and ignores surrounding whitespace. It returns
 * {@code null} for missing or unsupported extensions so callers can report an unsupported
 * language.
 *
 * <p>
 * Covered by {@code MatchExtensionTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class MatchExtension {

	private MatchExtension() {
	}

	/**
	 * Returns the runner class registered for an extension.
	 * @param ext the file extension without a leading dot; case and surrounding
	 * whitespace are ignored
	 * @return the matching runner class, or {@code null} when the extension is
	 * {@code null} or unsupported
	 */
	public static Class<? extends RunnerInterface> getRunnerFromExt(String ext) {
		if (ext == null) {
			return null;
		}

		return switch (ext.strip().toLowerCase(Locale.ROOT)) {
			case "java", "jav" -> JavaRunner.class;
			case "kt", "kts" -> KotlinRunner.class;
			case "groovy", "gvy", "gy", "gsh" -> GroovyRunner.class;
			default -> null;
		};
	}

}
