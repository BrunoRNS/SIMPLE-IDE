package io.simpleide.language.interpreter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.simpleide.language.AbstractLanguage;

/**
 * Base class for languages that execute source directly with an interpreter.
 *
 * <p>
 * Subclasses set {@link #interpreter} and call
 * {@link #configure(Path, String, String, String, List)} to build a single process
 * command made of the interpreter, optional additional arguments, and the absolute source
 * file path.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public abstract class AbstractInterpreterLanguage extends AbstractLanguage {

	protected List<String> additionalArgs = List.of();

	protected String interpreter;

	protected AbstractInterpreterLanguage() {
		super();
	}

	/**
	 * Configures the interpreter command for a source file.
	 * @param file the source file to execute
	 * @param friendlyName the human-readable language name
	 * @param languageConstant the syntax highlighting constant
	 * @param interpreter the interpreter executable
	 * @param additionalArgs arguments inserted before the source file path
	 * @throws IllegalArgumentException if {@code file} is {@code null}
	 */
	protected final void configure(Path file, String friendlyName, String languageConstant, String interpreter,
			List<String> additionalArgs) {
		if (file == null) {
			throw new IllegalArgumentException("Source file path must not be null.");
		}

		this.interpreter = interpreter;
		this.additionalArgs = List.copyOf(additionalArgs);
		this.langFriendly = friendlyName;
		this.langConstant = languageConstant;

		List<String> command = new ArrayList<>();
		command.add(interpreter);
		command.addAll(this.additionalArgs);
		command.add(file.toAbsolutePath().normalize().toString());
		this.pb = new ProcessBuilder(command);
	}

	/**
	 * Returns the configured interpreter executable.
	 * @return the interpreter command, or {@code null} when not configured
	 */
	public String getInterpreter() {
		return this.interpreter;
	}

	/**
	 * Returns the additional interpreter arguments.
	 * @return an unmodifiable list of arguments
	 */
	public List<String> getAdditionalArgs() {
		return additionalArgs;
	}

}
