package io.simpleide.language.interpreter;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Python 3 language support built on the system Python interpreter.
 *
 * <p>
 * The parameterless constructor selects {@code python3} on Linux and macOS, and
 * {@code python} on Windows. The {@link Path} constructor executes the given source file
 * directly with the selected interpreter. See docs/reference/LANGUAGES.md for the
 * required toolchain.
 *
 * <p>
 * Covered by {@code InterpreterLanguagesTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class Python3Language extends AbstractInterpreterLanguage {

	/**
	 * Creates a Python 3 language definition without a source file.
	 */
	public Python3Language() {
		interpreter = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
	}

	/**
	 * Creates a Python 3 language definition for the given source file.
	 * @param file the Python source file to execute
	 */
	public Python3Language(Path file) {
		this();
		configure(file, "python3", SyntaxConstants.SYNTAX_STYLE_PYTHON, interpreter, List.of());
	}

}
