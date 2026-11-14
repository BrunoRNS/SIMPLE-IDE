package io.simplebytecode.compiler.kotlin;

import io.simplebytecode.compiler.RunnerInterface;
import java.util.Objects;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class KotlinRunner implements RunnerInterface {

  private CompiledScript script;

  @Override
  public void run() {
    if (script == null) {
      throw new IllegalStateException("Kotlin source has not been prepared.");
    }
    try {
      script.eval();
    } catch (ScriptException exception) {
      throw new IllegalStateException("Could not run Kotlin script.", exception);
    }
  }

  @Override
  public void prepare(String src) {
    script = null;
    Objects.requireNonNull(src, "Kotlin source must not be null.");
    ScriptEngine engine =
        new ScriptEngineManager(Thread.currentThread().getContextClassLoader())
            .getEngineByExtension("kts");
    if (engine == null) {
      throw new IllegalStateException("Kotlin script engine is not available.");
    }
    if (!(engine instanceof Compilable compilable)) {
      throw new IllegalStateException("The Kotlin script engine does not support compilation.");
    }
    try {
      script = compilable.compile(src);
    } catch (ScriptException exception) {
      script = null;
      throw new IllegalArgumentException("Could not compile Kotlin script.", exception);
    }
  }
}
