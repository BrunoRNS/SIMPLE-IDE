package io.simpleide.language.interpreter;

/**
 * Reports the external interpreter required by an interpreter language.
 *
 * <p>
 * The utility instantiates a language class reflectively and returns its configured
 * interpreter command. Language setup uses the result to verify that the interpreter is
 * available on the system path.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class InterpreterRequiredFile {

	/**
	 * Returns the interpreter command required by a language class.
	 * @param i the interpreter language class to inspect, must not be {@code null}
	 * @return the configured interpreter command
	 * @throws IllegalArgumentException if {@code i} is {@code null}
	 * @throws IllegalStateException if the class cannot be instantiated or inspected
	 */
	public static String getInterpreter(Class<? extends AbstractInterpreterLanguage> i) {
		if (i == null) {
			throw new IllegalArgumentException("Interpreter class must not be null.");
		}

		try {
			AbstractInterpreterLanguage language = i.getDeclaredConstructor().newInstance();
			return language.getInterpreter();
		}
		catch (ReflectiveOperationException | SecurityException exception) {
			throw new IllegalStateException("Could not inspect interpreter requirements for " + i.getName(), exception);
		}
	}

}
