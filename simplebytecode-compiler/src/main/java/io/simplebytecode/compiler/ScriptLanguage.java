package io.simplebytecode.compiler;

import java.util.Objects;

public record ScriptLanguage(String id) {

    public static final ScriptLanguage GROOVY = new ScriptLanguage("groovy");
    public static final ScriptLanguage KOTLIN = new ScriptLanguage("kotlin");

    public ScriptLanguage {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
