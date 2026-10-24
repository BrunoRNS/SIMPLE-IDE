package io.simpleide.language.jvmbytecode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class KotlinBytecodeLoader implements BytecodeLoaderInterface {

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
    public void setInput(String input) {
        this.input = input == null ? "" : input;
    }

    @Override
    public String getOutput() {
        return outputBuffer.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void run() {
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "No Kotlin source loaded.");
        }

        outputBuffer.reset();

        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try (
                ByteArrayInputStream inputStream = new ByteArrayInputStream(
                        input.getBytes(StandardCharsets.UTF_8));
                PrintStream outputStream = new PrintStream(
                        outputBuffer, true, StandardCharsets.UTF_8)) {
            System.setIn(inputStream);
            System.setOut(outputStream);
            System.setErr(outputStream);

            ScriptEngine engine = new ScriptEngineManager()
                    .getEngineByExtension("kts");

            if (engine == null) {
                throw new IllegalStateException(
                        "Kotlin script engine is unavailable. "
                                + "Check the kotlin-scripting-jsr223 dependency "
                                + "and Kotlin version.");
            }

            engine.eval(source);

        } catch (ScriptException e) {
            throw new RuntimeException(
                    "Error executing Kotlin script.", e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Error managing program streams.", e);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

    }

}

