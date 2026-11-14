package io.simplebytecode.compiler.classloader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class InMemoryJavaCompiler {

  private InMemoryJavaCompiler() {}

  static ClassLoader compile(String className, String source) {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("A JDK is required to compile Java source.");
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    Map<String, ByteArrayOutputStream> classBytes = new HashMap<>();
    try (StandardJavaFileManager standardManager =
            compiler.getStandardFileManager(diagnostics, null, null);
        JavaFileManager fileManager = new MemoryFileManager(standardManager, classBytes)) {
      JavaFileObject sourceFile = new SourceFile(className, source);
      List<String> options =
          List.of("-proc:none", "-classpath", System.getProperty("java.class.path", ""));
      boolean compiled =
          compiler
              .getTask(null, fileManager, diagnostics, options, null, List.of(sourceFile))
              .call();
      if (!compiled) {
        String errors =
            diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(InMemoryJavaCompiler::formatDiagnostic)
                .reduce((first, second) -> first + System.lineSeparator() + second)
                .orElse("Unknown Java compilation error.");
        throw new IllegalArgumentException(
            "Could not compile Java source:" + System.lineSeparator() + errors);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not close the Java compiler file manager.", exception);
    }

    Map<String, byte[]> definitions = new HashMap<>();
    classBytes.forEach((name, bytes) -> definitions.put(name, bytes.toByteArray()));
    return new MemoryClassLoader(definitions, JavaRunner.class.getClassLoader());
  }

  private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
    return "line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null);
  }

  private static final class SourceFile extends SimpleJavaFileObject {
    private final String source;

    private SourceFile(String className, String source) {
      super(
          URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
          Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }

  private static final class ClassFile extends SimpleJavaFileObject {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private ClassFile(String className, Kind kind) {
      super(URI.create("memory:///" + className.replace('.', '/') + kind.extension), kind);
    }

    @Override
    public OutputStream openOutputStream() {
      return output;
    }
  }

  private static final class MemoryFileManager
      extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Map<String, ByteArrayOutputStream> classBytes;

    private MemoryFileManager(
        StandardJavaFileManager fileManager, Map<String, ByteArrayOutputStream> classBytes) {
      super(fileManager);
      this.classBytes = classBytes;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
        Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
      ClassFile classFile = new ClassFile(className, kind);
      classBytes.put(className, classFile.output);
      return classFile;
    }
  }

  private static final class MemoryClassLoader extends ClassLoader {
    private final Map<String, byte[]> definitions;

    private MemoryClassLoader(Map<String, byte[]> definitions, ClassLoader parent) {
      super(parent);
      this.definitions = definitions;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass == null) {
          loadedClass =
              definitions.containsKey(name) ? findClass(name) : super.loadClass(name, false);
        }
        if (resolve) {
          resolveClass(loadedClass);
        }
        return loadedClass;
      }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      byte[] bytecode = definitions.get(name);
      if (bytecode == null) {
        throw new ClassNotFoundException(name);
      }
      return defineClass(name, bytecode, 0, bytecode.length);
    }
  }
}
