
package io.simpleide.language.jvmbytecode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class GroovyBytecodeLoader implements BytecodeLoaderInterface {

    protected String source;

    private String input = "";
    private final ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();

    @Override
    public void loadFromSource(String source) {
        this.source = source;
    }

    @Override
    public void loadFromFile(Path filePath) {
        try {
            this.source = Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Error reading file: " + filePath, e);
        }
    }

    @Override
    public void run() {
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "No Groovy code loaded.");
        }

        outputBuffer.reset();

        try (PrintStream output = new PrintStream(
                outputBuffer, true, StandardCharsets.UTF_8)) {

            Binding binding = new Binding();
            binding.setProperty("input",
                    new java.util.Scanner(
                            new ByteArrayInputStream(
                                    input.getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8));
            binding.setProperty("output", output);

            GroovyShell shell = new GroovyShell(
                    getClass().getClassLoader(), binding);

            shell.evaluate(source);
        }
    }

    @Override
    public void setInput(String input) {
        this.input = input == null ? "" : input;
    }

    @Override
    public String getOutput() {
        return outputBuffer.toString(StandardCharsets.UTF_8);
    }
    
}
