package io.simplebytecode.compiler;

import javax.script.CompiledScript;

public interface ScriptCompiler {

    ScriptLanguage language();

    CompiledScript compile(ScriptSource source) throws CompilationException;
}
