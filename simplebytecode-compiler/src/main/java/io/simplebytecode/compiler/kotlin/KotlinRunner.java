package io.simplebytecode.compiler.kotlin;

import io.simplebytecode.compiler.RunnerInterface;
import java.util.Objects;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Compiles and evaluates Kotlin scripts through the JSR-223 script engine.
 *
 * <p>
 * {@link #prepare(String)} obtains the engine registered for the {@code kts} extension,
 * compiles the source and stores the {@link javax.script.CompiledScript}. {@link #run()}
 * evaluates the prepared script.
 *
 * <p>
 * Covered by {@code KotlinRunnerTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class KotlinRunner implements RunnerInterface {

	private CompiledScript script;

	/**
	 * Evaluates the prepared Kotlin script.
	 * @throws IllegalStateException if the source has not been prepared or evaluation
	 * fails
	 */
	@Override
	public void run() {
		if (script == null) {
			throw new IllegalStateException("Kotlin source has not been prepared.");
		}
		try {
			script.eval();
		}
		catch (ScriptException exception) {
			throw new IllegalStateException("Could not run Kotlin script.", exception);
		}
	}

	/**
	 * Compiles Kotlin source with the available script engine.
	 * @param src the Kotlin source text to prepare
	 * @throws NullPointerException if {@code src} is {@code null}
	 * @throws IllegalStateException if no Kotlin script engine is available or the engine
	 * cannot compile
	 * @throws IllegalArgumentException if the script cannot be compiled
	 */
	@Override
	public void prepare(String src) {
		script = null;
		Objects.requireNonNull(src, "Kotlin source must not be null.");
		ScriptEngine engine = new ScriptEngineManager(Thread.currentThread().getContextClassLoader())
			.getEngineByExtension("kts");
		if (engine == null) {
			throw new IllegalStateException("Kotlin script engine is not available.");
		}
		if (!(engine instanceof Compilable compilable)) {
			throw new IllegalStateException("The Kotlin script engine does not support compilation.");
		}
		try {
			script = compilable.compile(src);
		}
		catch (ScriptException exception) {
			script = null;
			throw new IllegalArgumentException("Could not compile Kotlin script.", exception);
		}
	}

}
