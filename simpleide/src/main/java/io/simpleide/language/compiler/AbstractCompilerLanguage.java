package io.simpleide.language.compiler;

import io.simpleide.language.AbstractLanguage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for languages that compile a source file before running it.
 *
 * <p>
 * The class prepares a temporary build directory, normalises the source path, and accepts
 * one or more compilation commands followed by an execution command through
 * {@link #configure(Path, String, String, Path, List, List)}. Temporary artifacts and the
 * build directory are removed after execution by {@link #onExecutionFinished()}.
 *
 * <p>
 * Subclasses normally set {@link #compiler}, {@link #linker}, {@link #assembler}, and
 * {@link #runtime} so that {@link CompilerRequiredFiles} can discover the required
 * toolchain.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public abstract class AbstractCompilerLanguage extends AbstractLanguage {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompilerLanguage.class);

	protected String executable;

	protected String compiler;

	protected String linker;

	protected String assembler;

	protected String runtime;

	protected List<String> execute = List.of();

	protected Path sourceFile;

	private Path artifactDirectory;

	@SuppressWarnings("unused")
	private Path executablePath;

	private final List<Path> artifacts = new ArrayList<>();

	protected AbstractCompilerLanguage() {
		super();
	}

	/**
	 * Creates a path for a build artifact inside the temporary artifact directory.
	 * @param name the artifact file name
	 * @return the resolved artifact path, registered for deletion on exit
	 * @throws IllegalArgumentException if {@code name} is {@code null} or blank
	 * @throws UncheckedIOException if the temporary directory cannot be created
	 */
	protected final Path createArtifactPath(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Artifact name must not be blank.");
		}
		if (artifactDirectory == null) {
			try {
				artifactDirectory = Files.createTempDirectory("simpleide-compiled-");
				artifactDirectory.toFile().deleteOnExit();
			}
			catch (IOException exception) {
				throw new UncheckedIOException("Could not create a temporary build directory.", exception);
			}
		}

		Path artifact = artifactDirectory.resolve(name);
		artifacts.add(artifact);
		artifact.toFile().deleteOnExit();
		return artifact;
	}

	/**
	 * Resolves and normalises a source file path.
	 * @param file the source file path
	 * @return the absolute, normalised path
	 * @throws IllegalArgumentException if {@code file} is {@code null}
	 */
	protected final Path normalizeSource(Path file) {
		if (file == null) {
			throw new IllegalArgumentException("Source file path must not be null.");
		}
		return file.toAbsolutePath().normalize();
	}

	/**
	 * Configures the source file, toolchain names, and process steps for this language.
	 * @param file the source file to compile
	 * @param friendlyName the human-readable language name
	 * @param languageConstant the syntax highlighting constant
	 * @param executablePath the compiled executable path
	 * @param compilationCommands the compilation commands to run in order
	 * @param executeCommand the command that runs the compiled executable
	 * @throws IllegalArgumentException if a required argument is missing or a command is
	 * empty
	 */
	protected final void configure(Path file, String friendlyName, String languageConstant, Path executablePath,
			List<List<String>> compilationCommands, List<String> executeCommand) {
		if (file == null) {
			throw new IllegalArgumentException("Source file path must not be null.");
		}
		if (executablePath == null || compilationCommands == null || compilationCommands.isEmpty()
				|| executeCommand == null || executeCommand.isEmpty()) {
			throw new IllegalArgumentException("Compiler and execution commands must be configured.");
		}

		sourceFile = normalizeSource(file);
		this.executablePath = executablePath;
		this.executable = executablePath.toString();
		this.langFriendly = friendlyName;
		this.langConstant = languageConstant;
		this.execute = List.copyOf(executeCommand);

		List<ProcessBuilder> builders = new ArrayList<>(compilationCommands.size() + 1);
		for (List<String> command : compilationCommands) {
			if (command == null || command.isEmpty()
					|| command.stream().anyMatch(arg -> arg == null || arg.isBlank())) {
				throw new IllegalArgumentException("Compilation commands must not be empty.");
			}
			builders.add(new ProcessBuilder(command));
		}
		builders.add(new ProcessBuilder(this.execute));
		processSteps = Collections.unmodifiableList(builders);
		pb = builders.get(0);
	}

	/**
	 * Returns the temporary artifact directory.
	 * @return the artifact directory, or {@code null} if no artifact has been created
	 */
	protected final Path getArtifactDirectory() {
		return artifactDirectory;
	}

	/**
	 * Reports whether the current operating system is Windows.
	 * @return {@code true} when the JVM reports a Windows operating system
	 */
	protected static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	/**
	 * Writes text contents to a build artifact.
	 * @param artifact the artifact path
	 * @param contents the text to write
	 * @throws UncheckedIOException if the file cannot be written
	 */
	protected final void writeArtifact(Path artifact, String contents) {
		try {
			Files.writeString(artifact, contents);
		}
		catch (IOException exception) {
			throw new UncheckedIOException("Could not write temporary compiler configuration.", exception);
		}
	}

	/**
	 * Returns the configured compiler command.
	 * @return the compiler command, or {@code null} when not configured
	 */
	public String getCompiler() {
		return compiler;
	}

	/**
	 * Returns the configured assembler command.
	 * @return the assembler command, or {@code null} when not configured
	 */
	public String getAssembler() {
		return assembler;
	}

	/**
	 * Returns the configured linker command.
	 * @return the linker command, or {@code null} when not configured
	 */
	public String getLinker() {
		return linker;
	}

	/**
	 * Returns the configured runtime command.
	 * @return the runtime command, or {@code null} when not configured
	 */
	public String getRuntime() {
		return runtime;
	}

	/**
	 * Returns the compiled executable path as a string.
	 * @return the executable path, or {@code null} when not configured
	 */
	public String getExecutable() {
		return executable;
	}

	/**
	 * Returns the command used to execute the compiled program.
	 * @return an unmodifiable execution command
	 */
	public List<String> execute() {
		return execute;
	}

	/**
	 * Returns the configured process commands.
	 * @return an immutable list of command argument lists
	 */
	final List<List<String>> getProcessCommands() {
		return processSteps.stream().map(step -> List.copyOf(step.command())).toList();
	}

	/**
	 * Removes temporary artifacts and resets the build state.
	 */
	@Override
	protected void onExecutionFinished() {
		for (Path artifact : artifacts) {
			try {
				Files.deleteIfExists(artifact);
			}
			catch (IOException exception) {
				LOGGER.warn("Could not remove temporary compiler artifact {}.", artifact, exception);
			}
		}
		if (artifactDirectory != null) {
			try {
				Files.deleteIfExists(artifactDirectory);
			}
			catch (IOException exception) {
				LOGGER.warn("Could not remove temporary build directory {}.", artifactDirectory, exception);
			}
		}
		artifacts.clear();
		executablePath = null;
		artifactDirectory = null;
	}

}
