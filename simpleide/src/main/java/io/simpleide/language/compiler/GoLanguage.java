package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Go language support built on the Go toolchain.
 *
 * <p>
 * The parameterless constructor configures the toolchain with {@code go}. The
 * {@link Path} constructor builds the given source file into a temporary executable with
 * {@code go build} and then runs it. See docs/reference/LANGUAGES.md for the required
 * toolchain.
 *
 * <p>
 * Covered by {@code CompilerLanguagesTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class GoLanguage extends AbstractCompilerLanguage {

	/**
	 * Creates a Go language definition without a source file.
	 */
	public GoLanguage() {
		compiler = "go";
	}

	/**
	 * Creates a Go language definition for the given source file.
	 * @param file the Go source file to build and run
	 */
	public GoLanguage(Path file) {
		this();
		Path source = normalizeSource(file);
		Path output = createArtifactPath("program.exe");
		configure(source, "go", SyntaxConstants.SYNTAX_STYLE_GO, output,
				List.of(List.of(compiler, "build", "-o", output.toString(), source.toString())),
				List.of(output.toString()));
	}

}
