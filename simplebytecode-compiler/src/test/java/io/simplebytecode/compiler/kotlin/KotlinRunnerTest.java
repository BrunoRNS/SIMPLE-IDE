package io.simplebytecode.compiler.kotlin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KotlinRunnerTest {

  @Test
  void preparesAndRunsKotlinScript() {
    KotlinRunner runner = new KotlinRunner();
    runner.prepare("println(\"kotlin runner works\")");

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    try {
      runner.run();
    } finally {
      System.setOut(originalOut);
    }

    assertThat(output.toString(StandardCharsets.UTF_8)).contains("kotlin runner works");
  }

  @Test
  void rejectsRunBeforePrepareAndInvalidScripts() {
    KotlinRunner runner = new KotlinRunner();
    assertThatThrownBy(runner::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not been prepared");
    assertThatThrownBy(() -> runner.prepare("val ="))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Could not compile Kotlin script");
    assertThatThrownBy(() -> runner.prepare(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(runner::run).isInstanceOf(IllegalStateException.class);
  }
}
