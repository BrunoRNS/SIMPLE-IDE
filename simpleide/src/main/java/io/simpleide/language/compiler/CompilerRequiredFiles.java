package io.simpleide.language.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports the external commands required by a compiler language.
 *
 * <p>
 * The utility instantiates a language class reflectively and collects its configured
 * compiler, assembler, linker, and runtime commands. Language setup uses the result to
 * verify that the toolchain is available on the system path.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class CompilerRequiredFiles {

	/**
	 * Returns the distinct toolchain executables required by a compiler language.
	 * @param c the compiler language class to inspect, must not be {@code null}
	 * @return an unmodifiable list of required commands, possibly empty
	 * @throws IllegalArgumentException if {@code c} is {@code null}
	 * @throws IllegalStateException if the class cannot be instantiated or inspected
	 */
	public static List<String> getRequiredFilesFromCompiler(Class<? extends AbstractCompilerLanguage> c) {
		if (c == null) {
			throw new IllegalArgumentException("Compiler class must not be null.");
		}

		try {
			AbstractCompilerLanguage language = c.getDeclaredConstructor().newInstance();
			List<String> required = new ArrayList<>(4);
			addIfPresent(required, language.getCompiler());
			addIfPresent(required, language.getAssembler());
			addIfPresent(required, language.getLinker());
			addIfPresent(required, language.getRuntime());
			return List.copyOf(required);
		}
		catch (ReflectiveOperationException | SecurityException exception) {
			throw new IllegalStateException("Could not inspect compiler requirements for " + c.getName(), exception);
		}
	}

	private static void addIfPresent(List<String> required, String command) {
		if (command != null && !command.isBlank() && !required.contains(command)) {
			required.add(command);
		}
	}

}
