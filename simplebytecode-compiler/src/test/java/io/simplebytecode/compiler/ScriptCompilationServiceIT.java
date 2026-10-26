package io.simplebytecode.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.script.CompiledScript;
import javax.script.ScriptException;

import org.junit.jupiter.api.Test;

class ScriptCompilationServiceIT {

    private final ScriptCompilationService service = ScriptCompilationService.createDefault();

    @Test
    void compilesAndExecutesGroovyScript() throws Exception {
        CompiledScript compiled = service.compile(
                new ScriptSource(ScriptLanguage.GROOVY, "'groovy-result'"));

        assertNotNull(compiled);
        assertEquals("groovy-result", compiled.eval());
    }

    @Test
    void compilesAndExecutesKotlinScript() throws Exception {
        CompiledScript compiled = service.compile(
                new ScriptSource(ScriptLanguage.KOTLIN, "val result = 42\nresult"));

        assertNotNull(compiled);
        assertNotNull(compiled.eval());
    }

    @Test
    void reportsInvalidGroovyAsCompilationFailure() {
        assertThrows(CompilationException.class,
                () -> service.compile(new ScriptSource(ScriptLanguage.GROOVY, "if (")));
    }
}
