package io.simpleide.language.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import io.simpleide.utils.OSpathUtil;

/**
 * C# language support built on the Mono compiler and runtime.
 *
 * <p>
 * The parameterless constructor configures {@code mcs} as the compiler and {@code mono}
 * as the runtime. The {@link Path} constructor compiles the given source file into a
 * temporary assembly and runs it with Mono. On Windows, an installed native Mono launcher
 * is reused so that batch-file launchers are bypassed. See docs/reference/LANGUAGES.md
 * for the required toolchain.
 *
 * <p>
 * Covered by {@code CompilerLanguagesTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class MonoCSharpLanguage extends AbstractCompilerLanguage {

	/**
	 * Creates a C# language definition without a source file.
	 */
	public MonoCSharpLanguage() {
		compiler = "mcs";
		runtime = "mono";
	}

	/**
	 * Creates a C# language definition for the given source file.
	 * @param file the C# source file to compile and run
	 */
	public MonoCSharpLanguage(Path file) {
		this();
		Path source = normalizeSource(file);
		Path output = createArtifactPath("program.exe");
		configure(source, "csharp", SyntaxConstants.SYNTAX_STYLE_CSHARP, output,
				List.of(compilerCommand(source, output)), runtimeCommand(output));
	}

	List<String> compilerCommand(Path source, Path output) {
		Path runtimeLauncher = windowsRuntimeLauncher();
		if (runtimeLauncher != null) {
			Path monoHome = runtimeLauncher.getParent().getParent();
			Path managedCompiler = monoHome.resolve("lib").resolve("mono").resolve("4.5").resolve("mcs.exe");
			if (Files.isRegularFile(managedCompiler)) {
				return List.of(runtimeLauncher.toString(), managedCompiler.toString(), source.toString(),
						"-out:" + output);
			}
		}
		return List.of(compiler, source.toString(), "-out:" + output);
	}

	private List<String> runtimeCommand(Path assembly) {
		Path runtimeLauncher = windowsRuntimeLauncher();
		return List.of(runtimeLauncher == null ? runtime : runtimeLauncher.toString(), assembly.toString());
	}

	private Path windowsRuntimeLauncher() {
		if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
			return null;
		}
		Path runtimeLauncher = OSpathUtil.which(runtime);
		return runtimeLauncher != null && !isBatchFile(runtimeLauncher) ? runtimeLauncher : null;
	}

	private boolean isBatchFile(Path path) {
		String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return fileName.endsWith(".bat") || fileName.endsWith(".cmd");
	}

}
