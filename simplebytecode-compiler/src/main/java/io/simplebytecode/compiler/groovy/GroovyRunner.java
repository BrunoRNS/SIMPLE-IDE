package io.simplebytecode.compiler.groovy;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.simplebytecode.compiler.RunnerInterface;
import java.util.Objects;

/**
 * Compiles and runs Groovy scripts.
 *
 * <p>
 * {@link #prepare(String)} parses the source into a {@link groovy.lang.Script} with a
 * {@link groovy.lang.GroovyShell}, and {@link #run()} executes the prepared script.
 *
 * <p>
 * Covered by {@code GroovyRunnerTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class GroovyRunner implements RunnerInterface {

	private Script script;

	/**
	 * Runs the prepared Groovy script.
	 * @throws IllegalStateException if the source has not been prepared
	 */
	@Override
	public void run() {
		if (script == null) {
			throw new IllegalStateException("Groovy source has not been prepared.");
		}
		script.run();
	}

	/**
	 * Parses Groovy source into an executable script.
	 * @param src the Groovy source text to prepare
	 * @throws NullPointerException if {@code src} is {@code null}
	 * @throws IllegalArgumentException if the script cannot be compiled
	 */
	@Override
	public void prepare(String src) {
		script = null;
		Objects.requireNonNull(src, "Groovy source must not be null.");
		try {
			script = new GroovyShell(Thread.currentThread().getContextClassLoader()).parse(src,
					"SimpleBytecodeScript.groovy");
		}
		catch (RuntimeException exception) {
			script = null;
			throw new IllegalArgumentException("Could not compile Groovy script.", exception);
		}
	}

}
