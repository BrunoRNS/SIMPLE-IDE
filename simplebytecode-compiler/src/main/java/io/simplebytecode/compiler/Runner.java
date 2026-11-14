package io.simplebytecode.compiler;

import io.simplebytecode.compiler.utils.MatchExtension;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Dispatches a source file to the runner that supports its extension.
 *
 * <p>
 * The constructor resolves the file extension with
 * {@link io.simplebytecode.compiler.utils.MatchExtension} and fails fast when no runner
 * is registered. {@link #run()} then creates the runner reflectively, loads the file
 * contents and executes the program.
 *
 * <p>
 * Covered by {@code RunnerTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class Runner implements Runnable {

	private final Class<? extends RunnerInterface> runner;

	private final Path file;

	/**
	 * Creates a runner for the given source file.
	 * @param file the source file to execute
	 * @throws NullPointerException if {@code file} is {@code null}
	 * @throws UnsupportedOperationException if no runner matches the file extension
	 */
	public Runner(Path file) {
		this.file = Objects.requireNonNull(file, "File path must not be null.").toAbsolutePath().normalize();
		this.runner = MatchExtension.getRunnerFromExt(getFileExtension(this.file));
		if (runner == null) {
			throw new UnsupportedOperationException(
					"No runner found for file extension: " + getFileExtension(this.file));
		}
	}

	/**
	 * Instantiates the matching runner, prepares it with the file contents and executes
	 * it.
	 * @throws IllegalStateException if the runner cannot be created or the file cannot be
	 * prepared
	 */
	@Override
	public void run() {
		try {
			RunnerInterface runnerInstance = runner.getDeclaredConstructor().newInstance();
			String src = runnerInstance.loadFile(file);
			runnerInstance.prepare(src);
			runnerInstance.run();
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not create runner: " + runner.getName(), exception);
		}
	}

	private String getFileExtension(Path file) {
		if (file.getFileName() == null) {
			return "";
		}
		String name = file.getFileName().toString();
		int lastIndex = name.lastIndexOf('.');
		if (lastIndex < 0 || lastIndex == name.length() - 1) {
			return "";
		}
		return name.substring(lastIndex + 1);
	}

}
