package io.simplebytecode.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimpleBytecodeCompilerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void reportsMissingAndUnsupportedInputFiles() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try (PrintStream errorStream = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setErr(errorStream);
            try {
                assertThat(SimpleBytecodeCompiler.execute()).isEqualTo(-1);
                assertThat(SimpleBytecodeCompiler.execute("source.unknown")).isEqualTo(-2);
            } finally {
                System.setErr(originalErr);
            }
        }

        assertThat(errors.toString(StandardCharsets.UTF_8))
            .contains("No file provided")
            .contains("No runner found for file extension: unknown");
    }

    @Test
    void executesJavaSourceFromTheCommandLine() throws Exception {
        Path sourceFile = tempDirectory.resolve("Main.java");
        Files.writeString(
            sourceFile,
            "public class Main { public static void main(String[] args) { System.out.println(\"cli java\"); } }"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            assertThat(SimpleBytecodeCompiler.execute(sourceFile.toString())).isZero();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("cli java");
    }

    @Test
    void returnsFailureForAnUnreadableFile() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try (PrintStream errorStream = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setErr(errorStream);
            try {
                assertThat(SimpleBytecodeCompiler.execute(tempDirectory.resolve("missing.java").toString()))
                    .isEqualTo(-5);
            } finally {
                System.setErr(originalErr);
            }
        }

        assertThat(errors.toString(StandardCharsets.UTF_8)).contains("Failed to read file:");
    }

}
