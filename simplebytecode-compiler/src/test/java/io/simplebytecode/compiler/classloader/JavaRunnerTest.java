package io.simplebytecode.compiler.classloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class JavaRunnerTest {

    @Test
    void compilesAndRunsAJavaMainClass() {
        JavaRunner runner = new JavaRunner();
        runner.prepare("""
            package test;
            public class Hello {
                public static void main(String[] args) {
                    System.out.println("java runner works");
                }
            }
            """);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            runner.run();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("java runner works");
    }

    @Test
    void rejectsRunBeforePrepareAndInvalidSources() {
        JavaRunner runner = new JavaRunner();
        assertThatThrownBy(runner::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not been prepared");
        assertThatThrownBy(() -> runner.prepare("public class Broken {"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not compile Java source");
        assertThatThrownBy(() -> runner.prepare("public class NoMain {}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("main(String[] args)");
        assertThatThrownBy(() -> runner.prepare(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(runner::run)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loadsCompiledClassesBeforeParentClassesWithTheSameName() {
        JavaRunner runner = new JavaRunner();
        runner.prepare("""
            package io.simplebytecode.compiler.classloader;
            public class JavaRunnerTest {
                public static void main(String[] args) {
                    System.out.println("compiled class loaded");
                }
            }
            """);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            runner.run();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("compiled class loaded");
    }
}
