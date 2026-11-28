package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * C++ language support built on the GNU C++ compiler.
 *
 * <p>
 * The parameterless constructor configures the toolchain with {@code g++}. The
 * {@link Path} constructor compiles the given source file into a temporary executable and
 * adds the static runtime flags on Windows. See docs/reference/LANGUAGES.md for the
 * required toolchain.
 *
 * <p>
 * Covered by {@code CompilerLanguagesTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class CPPLanguage extends AbstractCompilerLanguage {

	/**
	 * Creates a C++ language definition without a source file.
	 */
	public CPPLanguage() {
		compiler = "g++";
		linker = compiler;
	}

	/**
	 * Creates a C++ language definition for the given source file.
	 * @param file the C++ source file to compile and run
	 */
	public CPPLanguage(Path file) {
		this();
		Path source = normalizeSource(file);
		Path output = createArtifactPath("program.exe");
		List<String> command = new ArrayList<>(List.of(compiler, source.toString(), "-o", output.toString()));
		if (isWindows()) {
			command.add("-static-libgcc");
			command.add("-static-libstdc++");
		}
		configure(source, "c++", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS, output, List.of(command),
				List.of(output.toString()));
	}

}
