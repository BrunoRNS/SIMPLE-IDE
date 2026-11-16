package io.simpleide.language.interpreter;

import java.nio.file.Path;
import java.util.List;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * JavaScript language support built on the Node.js runtime.
 *
 * <p>
 * The parameterless constructor configures {@code node} as the interpreter. The
 * {@link Path} constructor executes the given source file directly with Node.js. See
 * docs/reference/LANGUAGES.md for the required toolchain.
 *
 * <p>
 * Covered by {@code InterpreterLanguagesTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class JSLanguage extends AbstractInterpreterLanguage {

	/**
	 * Creates a JavaScript language definition without a source file.
	 */
	public JSLanguage() {
		interpreter = "node";
	}

	/**
	 * Creates a JavaScript language definition for the given source file.
	 * @param file the JavaScript source file to execute
	 */
	public JSLanguage(Path file) {
		this();
		configure(file, "javascript", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT, interpreter, List.of());
	}

}
