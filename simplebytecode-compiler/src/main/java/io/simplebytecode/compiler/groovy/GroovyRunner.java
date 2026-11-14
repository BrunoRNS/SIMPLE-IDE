package io.simplebytecode.compiler.groovy;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.simplebytecode.compiler.RunnerInterface;
import java.util.Objects;

public class GroovyRunner implements RunnerInterface {

  private Script script;

  @Override
  public void run() {
    if (script == null) {
      throw new IllegalStateException("Groovy source has not been prepared.");
    }
    script.run();
  }

  @Override
  public void prepare(String src) {
    script = null;
    Objects.requireNonNull(src, "Groovy source must not be null.");
    try {
      script =
          new GroovyShell(Thread.currentThread().getContextClassLoader())
              .parse(src, "SimpleBytecodeScript.groovy");
    } catch (RuntimeException exception) {
      script = null;
      throw new IllegalArgumentException("Could not compile Groovy script.", exception);
    }
  }
}
