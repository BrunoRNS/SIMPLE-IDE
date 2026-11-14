package io.simpleide.language.interpreter;

public class InterpreterRequiredFile {

  public static String getInterpreter(Class<? extends AbstractInterpreterLanguage> i) {
    if (i == null) {
      throw new IllegalArgumentException("Interpreter class must not be null.");
    }

    try {
      AbstractInterpreterLanguage language = i.getDeclaredConstructor().newInstance();
      return language.getInterpreter();
    } catch (ReflectiveOperationException | SecurityException exception) {
      throw new IllegalStateException(
          "Could not inspect interpreter requirements for " + i.getName(), exception);
    }
  }
}
