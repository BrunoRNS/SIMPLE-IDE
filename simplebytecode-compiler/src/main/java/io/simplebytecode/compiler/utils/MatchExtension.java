package io.simplebytecode.compiler.utils;

import io.simplebytecode.compiler.RunnerInterface;
import io.simplebytecode.compiler.classloader.JavaRunner;
import io.simplebytecode.compiler.groovy.GroovyRunner;
import io.simplebytecode.compiler.kotlin.KotlinRunner;
import java.util.Locale;

public final class MatchExtension {

  private MatchExtension() {}

  public static Class<? extends RunnerInterface> getRunnerFromExt(String ext) {
    if (ext == null) {
      return null;
    }

    return switch (ext.strip().toLowerCase(Locale.ROOT)) {
      case "java", "jav" -> JavaRunner.class;
      case "kt", "kts" -> KotlinRunner.class;
      case "groovy", "gvy", "gy", "gsh" -> GroovyRunner.class;
      default -> null;
    };
  }
}
