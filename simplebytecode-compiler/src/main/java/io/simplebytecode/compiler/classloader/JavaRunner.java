package io.simplebytecode.compiler.classloader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

import io.simplebytecode.compiler.RunnerInterface;

public class JavaRunner implements RunnerInterface {

    private Method mainMethod;

    @Override
    public void run() {
        if (mainMethod == null) {
            throw new IllegalStateException("Java source has not been prepared.");
        }
        try {
            mainMethod.invoke(null, (Object) new String[0]);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not access the Java main method.", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Java main method failed.", cause);
        }
    }

    @Override
    public void prepare(String src) {
        mainMethod = null;
        Objects.requireNonNull(src, "Java source must not be null.");
        String className = JavaSourceClassName.findMainClass(src);
        if (className == null) {
            throw new IllegalArgumentException("Java source must declare a public class.");
        }

        ClassLoader classLoader = InMemoryJavaCompiler.compile(className, src);
        try {
            Class<?> mainClass = classLoader.loadClass(className);
            mainMethod = mainClass.getMethod("main", String[].class);
        } catch (ReflectiveOperationException exception) {
            mainMethod = null;
            throw new IllegalArgumentException(
                "Java class must declare a public static void main(String[] args) method: " + className,
                exception
            );
        }

        int modifiers = mainMethod.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
            || mainMethod.getReturnType() != void.class) {
            mainMethod = null;
            throw new IllegalArgumentException(
                "Java class must declare a public static void main(String[] args) method: " + className
            );
        }
    }
}
