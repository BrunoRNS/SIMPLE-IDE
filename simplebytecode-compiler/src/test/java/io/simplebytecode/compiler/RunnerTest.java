package io.simplebytecode.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerTest {

  @TempDir Path tempDirectory;

  @Test
  void selectsRunnerFromFileExtensionAndRunsTheFile() throws Exception {
    Path sourceFile = tempDirectory.resolve("Main.JAVA");
    Files.writeString(
        sourceFile,
        "public class Main { public static void main(String[] args) { System.out.println(\"runner java\"); } }");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    try {
      new Runner(sourceFile).run();
    } finally {
      System.setOut(originalOut);
    }

    assertThat(output.toString(StandardCharsets.UTF_8)).contains("runner java");
  }

  @Test
  void rejectsUnsupportedExtensionsAndNullPaths() {
    assertThatThrownBy(() -> new Runner(Path.of("source.txt")))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("txt");
    assertThatThrownBy(() -> new Runner(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void reportsSourceLoadingAndPreparationFailures() throws Exception {
    assertThatThrownBy(() -> new Runner(tempDirectory.resolve("missing.java")).run())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to read file");

    Path brokenSource = tempDirectory.resolve("Broken.java");
    Files.writeString(brokenSource, "public class Broken {");
    assertThatThrownBy(() -> new Runner(brokenSource).run())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Could not compile Java source");
  }
}
