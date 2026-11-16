package io.simplebytecode.compiler.classloader;

import io.simplebytecode.compiler.RunnerInterface;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Executes Java source compiled in memory.
 *
 * <p>
 * {@link #prepare(String)} finds the main class, compiles the source with
 * {@link io.simplebytecode.compiler.classloader.InMemoryJavaCompiler} and locates the
 * standard {@code public static void main(String[] args)} method. {@link #run()} then
 * invokes that method.
 *
 * <p>
 * Covered by {@code JavaRunnerTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public class JavaRunner implements RunnerInterface {

	private Method mainMethod;

	/**
	 * Invokes the prepared {@code main} method.
	 * @throws IllegalStateException if the source has not been prepared, the method is
	 * inaccessible, or it fails with a checked exception
	 */
	@Override
	public void run() {
		if (mainMethod == null) {
			throw new IllegalStateException("Java source has not been prepared.");
		}
		try {
			mainMethod.invoke(null, (Object) new String[0]);
		}
		catch (IllegalAccessException exception) {
			throw new IllegalStateException("Could not access the Java main method.", exception);
		}
		catch (InvocationTargetException exception) {
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

	/**
	 * Compiles Java source and resolves its public static {@code main} method.
	 * @param src the Java source text to prepare
	 * @throws NullPointerException if {@code src} is {@code null}
	 * @throws IllegalArgumentException if the source has no public class, cannot be
	 * compiled, or does not declare a {@code public static void main(String[] args)}
	 * method
	 */
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
		}
		catch (ReflectiveOperationException exception) {
			mainMethod = null;
			throw new IllegalArgumentException(
					"Java class must declare a public static void main(String[] args) method: " + className, exception);
		}

		int modifiers = mainMethod.getModifiers();
		if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
				|| mainMethod.getReturnType() != void.class) {
			mainMethod = null;
			throw new IllegalArgumentException(
					"Java class must declare a public static void main(String[] args) method: " + className);
		}
	}

}
