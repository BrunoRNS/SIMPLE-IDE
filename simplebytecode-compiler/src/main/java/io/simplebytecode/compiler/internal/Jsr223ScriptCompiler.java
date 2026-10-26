package io.simplebytecode.compiler.internal;

import java.util.Objects;
import java.util.function.Supplier;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import io.simplebytecode.compiler.CompilationException;
import io.simplebytecode.compiler.ScriptCompiler;
import io.simplebytecode.compiler.ScriptLanguage;
import io.simplebytecode.compiler.ScriptSource;

public final class Jsr223ScriptCompiler implements ScriptCompiler {

    private final ScriptLanguage language;
    private final Supplier<ScriptEngine> engineProvider;

    public Jsr223ScriptCompiler(ScriptLanguage language, Supplier<ScriptEngine> engineProvider) {
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.engineProvider = Objects.requireNonNull(engineProvider, "engineProvider must not be null");
    }

    @Override
    public ScriptLanguage language() {
        return language;
    }

    @Override
    public CompiledScript compile(ScriptSource source) throws CompilationException {
        Objects.requireNonNull(source, "source must not be null");
        if (source.language() != language) {
            throw new CompilationException(
                    "Compiler for " + language + " cannot compile " + source.language() + " source");
        }

        ScriptEngine engine = engineProvider.get();
        if (!(engine instanceof Compilable compilable)) {
            throw new CompilationException("No JSR-223 compiler available for " + language);
        }

        try {
            return compilable.compile(source.source());
        } catch (ScriptException e) {
            throw new CompilationException("Failed to compile " + language + " script", e);
        }
    }
}
