package io.simpleide.language.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * Rust language support built on {@code rustc}.
 *
 * <p>
 * The parameterless constructor configures the toolchain with {@code rustc}. The
 * {@link Path} constructor compiles the given source file into a temporary executable. On
 * 32-bit Windows, the compiler is invoked with the {@code i686-pc-windows-gnu} target and
 * the {@code gcc} linker. See docs/reference/LANGUAGES.md for details.
 *
 * <p>
 * Covered by {@code CompilerLanguagesTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class RustLanguage extends AbstractCompilerLanguage {

	/**
	 * Creates a Rust language definition without a source file.
	 */
	public RustLanguage() {
		compiler = "rustc";
	}

	/**
	 * Creates a Rust language definition for the given source file.
	 * @param file the Rust source file to compile and run
	 */
	public RustLanguage(Path file) {
		this();
		Path source = normalizeSource(file);
		Path output = createArtifactPath("program.exe");
		configure(source, "rust", SyntaxConstants.SYNTAX_STYLE_RUST, output, List.of(compilerCommand(source, output)),
				List.of(output.toString()));
	}

	List<String> compilerCommand(Path source, Path output) {
		List<String> command = new ArrayList<>(List.of(compiler));
		String target = windows32Target(System.getProperty("os.name"), System.getProperty("os.arch"));
		if (target != null) {
			command.add("--target=" + target);
			command.add("-C");
			command.add("linker=gcc");
		}
		command.add(source.toString());
		command.add("-o");
		command.add(output.toString());
		return List.copyOf(command);
	}

	/**
	 * Returns the GNU Rust target used for 32-bit Windows builds.
	 * @param osName the operating system name reported by the JVM
	 * @param architecture the processor architecture reported by the JVM
	 * @return the Rust target triple, or {@code null} when no special target is needed
	 */
	static String windows32Target(String osName, String architecture) {
		if (!osName.toLowerCase(Locale.ROOT).contains("win")) {
			return null;
		}
		return switch (architecture.toLowerCase(Locale.ROOT)) {
			case "x86", "i386", "i486", "i586", "i686" -> "i686-pc-windows-gnu";
			default -> null;
		};
	}

}
