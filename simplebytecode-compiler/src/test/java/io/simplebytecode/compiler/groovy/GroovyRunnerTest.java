package io.simplebytecode.compiler.groovy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class GroovyRunnerTest {

    @Test
    void preparesAndRunsGroovyScript() {
        GroovyRunner runner = new GroovyRunner();
        runner.prepare("println 'groovy runner works'");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            runner.run();
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("groovy runner works");
    }

    @Test
    void rejectsRunBeforePrepareAndInvalidScripts() {
        GroovyRunner runner = new GroovyRunner();
        assertThatThrownBy(runner::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not been prepared");
        assertThatThrownBy(() -> runner.prepare("if ("))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Could not compile Groovy script");
        assertThatThrownBy(() -> runner.prepare(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(runner::run)
            .isInstanceOf(IllegalStateException.class);
    }
}
