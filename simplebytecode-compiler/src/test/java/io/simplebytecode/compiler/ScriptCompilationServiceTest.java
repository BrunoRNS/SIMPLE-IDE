package io.simplebytecode.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;

import org.junit.jupiter.api.Test;

class ScriptCompilationServiceTest {

    @Test
    void dispatchesCompilationToRegisteredLanguageCompiler() throws Exception {
        CompiledScript compiled = new CompiledScript() {
            @Override
            public Object eval(ScriptContext context) {
                return 1;
            }

            @Override
            public ScriptEngine getEngine() {
                return null;
            }
        };
        ScriptSource source = new ScriptSource(ScriptLanguage.GROOVY, "return 1");
        ScriptCompiler compiler = new ScriptCompiler() {
            @Override
            public ScriptLanguage language() {
                return ScriptLanguage.GROOVY;
            }

            @Override
            public CompiledScript compile(ScriptSource requested) {
                return compiled;
            }
        };

        ScriptCompilationService service = new ScriptCompilationService(List.of(compiler));

        assertEquals(compiled, service.compile(source));
    }

    @Test
    void rejectsDuplicateCompilersForOneLanguage() {
        ScriptCompiler first = compilerFor(ScriptLanguage.GROOVY);
        ScriptCompiler second = compilerFor(ScriptLanguage.GROOVY);

        assertThrows(IllegalArgumentException.class,
                () -> new ScriptCompilationService(List.of(first, second)));
    }

    @Test
    void rejectsBlankSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptSource(ScriptLanguage.KOTLIN, "  "));
    }

    private ScriptCompiler compilerFor(ScriptLanguage language) {
        return new ScriptCompiler() {
            @Override
            public ScriptLanguage language() {
                return language;
            }

            @Override
            public CompiledScript compile(ScriptSource source) {
                throw new UnsupportedOperationException("This test compiler is only used for registration");
            }
        };
    }
}
