package io.simplebytecode.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.script.CompiledScript;
import javax.script.ScriptEngineManager;

import io.simplebytecode.compiler.internal.Jsr223ScriptCompiler;

public final class ScriptCompilationService {

    private final Map<ScriptLanguage, ScriptCompiler> compilers;

    public ScriptCompilationService(List<ScriptCompiler> compilers) {
        Objects.requireNonNull(compilers, "compilers must not be null");
        Map<ScriptLanguage, ScriptCompiler> registered = new HashMap<>();
        for (ScriptCompiler compiler : compilers) {
            Objects.requireNonNull(compiler, "compiler must not be null");
            ScriptLanguage language = Objects.requireNonNull(
                    compiler.language(), "compiler language must not be null");
            if (registered.putIfAbsent(language, compiler) != null) {
                throw new IllegalArgumentException("A compiler is already registered for " + language);
            }
        }
        this.compilers = Map.copyOf(registered);
    }

    public static ScriptCompilationService createDefault() {
        return new ScriptCompilationService(List.of(
                new Jsr223ScriptCompiler(ScriptLanguage.GROOVY,
                        () -> new ScriptEngineManager()
                                .getEngineByName(ScriptLanguage.GROOVY.id())),
                new Jsr223ScriptCompiler(ScriptLanguage.KOTLIN,
                        () -> new ScriptEngineManager()
                                .getEngineByName(ScriptLanguage.KOTLIN.id()))));
    }

    public CompiledScript compile(ScriptSource source) throws CompilationException {
        Objects.requireNonNull(source, "source must not be null");
        ScriptCompiler compiler = compilers.get(source.language());
        if (compiler == null) {
            throw new CompilationException("No compiler registered for " + source.language());
        }
        return compiler.compile(source);
    }
}
