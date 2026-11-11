package io.simpleide.ui.terminal;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import com.pty4j.PtyProcessBuilder;

public final class Pty4jCompatibility {

    private Pty4jCompatibility() {
    }

    public static PtyProcessBuilder setSpawnProcessUsingJdkOnMacIntel(
        PtyProcessBuilder builder,
        boolean enabled
    ) {
        Objects.requireNonNull(builder, "PTY process builder must not be null");
        try {
            builder.getClass()
                .getMethod("setSpawnProcessUsingJdkOnMacIntel", boolean.class)
                .invoke(builder, enabled);
        } catch (NoSuchMethodException ignored) {
            // Pty4J21 does not expose this upstream-specific macOS Intel option.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Unable to configure the pty4j process builder.", exception);
        }
        return builder;
    }
}
