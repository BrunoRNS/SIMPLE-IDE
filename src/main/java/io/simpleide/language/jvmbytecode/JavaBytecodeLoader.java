
package io.simpleide.language.jvmbytecode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class JavaBytecodeLoader implements BytecodeLoaderInterface {

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
                    "No Java source loaded.");
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler == null) {
            throw new IllegalStateException(
                    "JavaCompiler unavailable. Run with a JDK.");
        }

        String packageName = extractPackageName(source);
        String className = extractClassName(source);

        String fullClassName = packageName.isEmpty()
                ? className
                : packageName + "." + className;

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        Map<String, ByteCodeObject> compiledClasses = new HashMap<>();

        try (StandardJavaFileManager standardManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8);
                MemoryFileManager fileManager = new MemoryFileManager(
                        standardManager, compiledClasses)) {

            JavaFileObject sourceFile = new SourceCodeObject(fullClassName, source);

            Boolean success = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    java.util.List.of(
                            "-proc:none",
                            "-classpath",
                            System.getProperty("java.class.path")),
                    null,
                    java.util.List.of(sourceFile)).call();

            if (!Boolean.TRUE.equals(success)) {
                StringBuilder message = new StringBuilder("Java compilation failed:");

                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    message.append(System.lineSeparator())
                            .append("Line ")
                            .append(diagnostic.getLineNumber())
                            .append(": ")
                            .append(diagnostic.getMessage(null));
                }

                throw new RuntimeException(message.toString());
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Error compiling Java source.", e);
        }

        outputBuffer.reset();

        java.io.InputStream originalIn = System.in;
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

            ClassLoader classLoader = new MemoryClassLoader(
                    getClass().getClassLoader(), compiledClasses);

            Class<?> programClass = Class.forName(
                    fullClassName, true, classLoader);

            Method main = programClass.getMethod(
                    "main", String[].class);

            main.invoke(null, (Object) new String[0]);

        } catch (InvocationTargetException e) {
            throw new RuntimeException(
                    "Error executing Java program.", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Could not find or invoke public static "
                            + "void main(String[] args).",
                    e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Error managing program streams.", e);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private String extractPackageName(String source) {
        Matcher matcher = Pattern.compile(
                "(?m)^\\s*package\\s+([\\w.]+)\\s*;")
                .matcher(source);

        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractClassName(String source) {
        Pattern pattern = Pattern.compile(
                "\\b(?:(?:public|abstract|final|sealed|strictfp)\\s+)*"
                        + "class\\s+(\\w+)");

        Matcher matcher = pattern.matcher(source);

        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new IllegalArgumentException(
                "No Java class declaration found.");
    }

    private static class SourceCodeObject
            extends SimpleJavaFileObject {

        private final String source;

        SourceCodeObject(String className, String source) {
            super(URI.create(
                    "string:///"
                            + className.replace('.', '/')
                            + Kind.SOURCE.extension),
                    Kind.SOURCE);

            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static class ByteCodeObject
            extends SimpleJavaFileObject {

        private final ByteArrayOutputStream bytecode = new ByteArrayOutputStream();

        ByteCodeObject(String className, Kind kind) {
            super(URI.create(
                    "memory:///"
                            + className.replace('.', '/')
                            + kind.extension),
                    kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytecode;
        }

        byte[] getBytes() {
            return bytecode.toByteArray();
        }
    }

    private static class MemoryFileManager
            extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Map<String, ByteCodeObject> compiledClasses;

        MemoryFileManager(
                StandardJavaFileManager manager,
                Map<String, ByteCodeObject> compiledClasses) {

            super(manager);
            this.compiledClasses = compiledClasses;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling) {

            ByteCodeObject output = new ByteCodeObject(className, kind);

            compiledClasses.put(className, output);

            return output;
        }
    }

    private static class MemoryClassLoader extends ClassLoader {

        private final Map<String, ByteCodeObject> compiledClasses;

        MemoryClassLoader(
                ClassLoader parent,
                Map<String, ByteCodeObject> compiledClasses) {

            super(parent);
            this.compiledClasses = compiledClasses;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {

            ByteCodeObject bytecode = compiledClasses.get(name);

            if (bytecode == null) {
                return super.findClass(name);
            }

            byte[] bytes = bytecode.getBytes();

            return defineClass(name, bytes, 0, bytes.length);
        }
        
    }

}
