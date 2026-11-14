package io.simpleide.language.jvmbytecode;

import java.nio.file.Path;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Java language support executed through the embedded bytecode compiler.
 *
 * <p>
 * The compiler runs the given Java source file with the current Java executable, so no
 * separate compilation step is required. See docs/reference/LANGUAGES.md for the runtime
 * requirements.
 *
 * <p>
 * Covered by {@code JvmBytecodeLanguageTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class JavaLanguage extends AbstractBytecodeLanguage {

	/**
	 * Creates a Java language definition for the given source file.
	 * @param filePath the Java source file to execute
	 */
	public JavaLanguage(Path filePath) {
		super(filePath, "java", SyntaxConstants.SYNTAX_STYLE_JAVA);
	}

}
