package io.simplebytecode.compiler;

import io.simplebytecode.compiler.utils.FileUtil;
import java.nio.file.Path;

/**
 * Common contract for language runners that prepare and execute source code.
 *
 * <p>
 * A runner is created reflectively by {@link io.simplebytecode.compiler.Runner} or looked
 * up by file extension through {@link io.simplebytecode.compiler.utils.MatchExtension}.
 * Implementations must therefore expose a public no-argument constructor.
 *
 * <p>
 * Covered by {@code RunnerInterfaceTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public interface RunnerInterface extends Runnable {

	/**
	 * Loads source code into this runner so that {@link #run()} can execute it.
	 * @param src the complete source text to prepare
	 * @throws IllegalArgumentException if the source cannot be parsed or compiled
	 * @throws IllegalStateException if a required language runtime is unavailable
	 */
	void prepare(String src);

	/**
	 * Reads a source file as strictly decoded UTF-8 text.
	 * @param file the file to read
	 * @return the file contents
	 * @throws IllegalArgumentException if the file cannot be read
	 */
	default String loadFile(Path file) {
		try {
			return FileUtil.readString(file);
		}
		catch (java.io.IOException e) {
			throw new IllegalArgumentException("Failed to read file: " + file, e);
		}
	}

}
