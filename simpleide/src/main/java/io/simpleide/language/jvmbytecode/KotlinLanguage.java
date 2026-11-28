package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Kotlin language support executed through the embedded bytecode compiler.
 *
 * <p>
 * The compiler runs the given Kotlin source file with the current Java executable, so no
 * external Kotlin installation is required. See docs/reference/LANGUAGES.md for the
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
public final class KotlinLanguage extends AbstractBytecodeLanguage {

	/**
	 * Creates a Kotlin language definition for the given source file.
	 * @param filePath the Kotlin source file to execute
	 */
	public KotlinLanguage(Path filePath) {
		super(filePath, "kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
	}

}
