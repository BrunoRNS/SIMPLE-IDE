package io.simplebytecode.compiler;

import java.nio.file.Path;

/**
 * Command-line entry point for the standalone bytecode compiler.
 *
 * <p>
 * The compiler accepts a single source file path, selects a runner through {@link Runner}
 * and executes the program. Exit codes report success or the kind of failure to the
 * calling process.
 *
 * <p>
 * Covered by {@code SimpleBytecodeCompilerTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 * @author luisotavio15
 * @since v1.0.0beta
 */
public final class SimpleBytecodeCompiler {

	/**
	 * Runs the compiler and terminates the JVM with the resulting exit code when it is
	 * non-zero.
	 * @param args command-line arguments; the first element is the source file to execute
	 */
	public static void main(String[] args) {
		int exitCode = execute(args);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	/**
	 * Executes the source file described by the arguments without terminating the JVM.
	 * @param args command-line arguments; the first element is the source file to execute
	 * @return {@code 0} on success, {@code -1} when no file is provided, {@code -2} when
	 * no runner supports the extension, or {@code -5} for other runtime failures
	 */
	public static int execute(String... args) {
		if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
			System.err.println("ERROR: No file provided");
			return -1;
		}

		try {
			new Runner(Path.of(args[0])).run();
			return 0;
		}
		catch (UnsupportedOperationException exception) {
			System.err.println("ERROR: " + exception.getMessage());
			return -2;
		}
		catch (RuntimeException exception) {
			String message = exception.getMessage() == null ? exception.getClass().getSimpleName()
					: exception.getMessage();
			System.err.println("ERROR: " + message);
			return -5;
		}
	}

}
