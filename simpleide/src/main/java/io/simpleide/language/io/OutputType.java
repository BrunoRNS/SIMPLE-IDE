package io.simpleide.language.io;

/**
 * Classification of a line published by a language process.
 *
 * <p>
 * The type lets the I/O tab colour program output without parsing log prefixes. Standard
 * program output uses {@link #OUTPUT}, failures and runtime errors use {@link #ERROR},
 * and diagnostic messages produced by the IDE use {@link #DEBUG}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public enum OutputType {

	/**
	 * Normal output produced by the running program.
	 */
	OUTPUT,

	/**
	 * An error produced by the running program or by its process pipeline.
	 */
	ERROR,

	/**
	 * A diagnostic message produced by the IDE while running the program.
	 */
	DEBUG

}
