/**
 * In-memory Java compilation and execution support.
 *
 * <p>
 * {@link io.simplebytecode.compiler.classloader.InMemoryJavaCompiler} compiles Java
 * source with the system {@link javax.tools.JavaCompiler} and captures the generated
 * class files in memory through a custom file manager and class loader. No class file is
 * written to disk. {@link io.simplebytecode.compiler.classloader.JavaRunner} uses that
 * compiler to load the program and invoke its {@code main} method.
 * {@link io.simplebytecode.compiler.classloader.JavaSourceClassName} inspects the source
 * to find the public class name that must be compiled, including when the file name and
 * the class name differ.
 *
 * <p>
 * Compilation errors are reported as {@link IllegalArgumentException} with the compiler
 * diagnostics, and missing classes or failed reflective invocation surface as
 * {@link IllegalStateException}. The classes produced by the in-memory loader are
 * isolated from the application class loader; static state is therefore not shared with
 * the running IDE.
 *
 * <p>
 * The package does not create threads. Compilation and execution run on the calling
 * thread, and each call creates a fresh class loader so repeated executions do not reuse
 * previously loaded classes.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/compiler/README.md">docs/api/compiler/README.md</a>
 */
package io.simplebytecode.compiler.classloader;
