package io.simpleide.ui.terminal;

import com.pty4j.PtyProcessBuilder;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Compatibility helpers for different pty4j versions.
 *
 * <p>
 * Some pty4j releases expose Intel macOS options that others do not. The helper applies
 * the option reflectively and ignores the absence of the method, so the same code runs
 * against both library variants.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class Pty4jCompatibility {

	private Pty4jCompatibility() {
	}

	/**
	 * Enables or disables the JDK-based spawn mode on Intel macOS when supported.
	 * @param builder PTY process builder to configure
	 * @param enabled desired option value
	 * @return the supplied builder
	 * @throws IllegalStateException when the option exists but cannot be invoked
	 */
	public static PtyProcessBuilder setSpawnProcessUsingJdkOnMacIntel(PtyProcessBuilder builder, boolean enabled) {
		Objects.requireNonNull(builder, "PTY process builder must not be null");
		try {
			builder.getClass().getMethod("setSpawnProcessUsingJdkOnMacIntel", boolean.class).invoke(builder, enabled);
		}
		catch (NoSuchMethodException ignored) {
			// Pty4J21 does not expose this upstream-specific macOS Intel option.
		}
		catch (IllegalAccessException | InvocationTargetException exception) {
			throw new IllegalStateException("Unable to configure the pty4j process builder.", exception);
		}
		return builder;
	}

}
