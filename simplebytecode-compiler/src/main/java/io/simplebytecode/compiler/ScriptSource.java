package io.simplebytecode.compiler;

import java.util.Objects;

public record ScriptSource(ScriptLanguage language, String source) {

    public ScriptSource {
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
    }
}
