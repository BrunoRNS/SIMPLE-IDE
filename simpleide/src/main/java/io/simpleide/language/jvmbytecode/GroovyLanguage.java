package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Groovy language support executed through the embedded bytecode compiler.
 *
 * <p>
 * The compiler runs the given Groovy source file with the current Java executable, so no
 * external Groovy installation is required. See docs/reference/LANGUAGES.md for the
 * runtime requirements.
 *
 * <p>
 * Covered by {@code JvmBytecodeLanguageTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class GroovyLanguage extends AbstractBytecodeLanguage {

	/**
	 * Creates a Groovy language definition for the given source file.
	 * @param filePath the Groovy source file to execute
	 */
	public GroovyLanguage(Path filePath) {
		super(filePath, "groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY);
	}

}
