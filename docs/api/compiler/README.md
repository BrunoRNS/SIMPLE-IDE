# Bytecode compiler module

`simplebytecode-compiler` is the standalone module that compiles and runs Java,
Kotlin and Groovy source without writing class files first. It is used by the IDE
for JVM languages and can be consumed on its own.

## Artifacts

| Artifact | Content | Use when |
| --- | --- | --- |
| `simplebytecode-compiler` | Compiler, runners and no shaded dependencies | You already manage Groovy and Kotlin |
| `simplebytecode-compiler` `jar-with-dependencies` | Compiler plus Groovy and Kotlin runtime | Running the compiler standalone |

See [Artifacts and imports](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Artifacts) for installation details.

## Command line

`io.simplebytecode.compiler.SimpleBytecodeCompiler` accepts one source file:

```sh
java -jar simplebytecode-compiler-1.0.0beta-jar-with-dependencies.jar Example.java
```

| Exit code | Meaning |
| --- | --- |
| `0` | The program ran successfully |
| `-1` | No file was provided |
| `-2` | No runner supports the file extension |
| `-5` | The program failed at runtime |

`SimpleBytecodeCompiler.execute(String...)` returns the same codes without
terminating the JVM, which makes it usable from tests and embedding code.

## Dispatch

`Runner` resolves the file extension through `MatchExtension` and fails fast when
no runner is registered. On `run()` it reflectively creates the runner, loads the
file as strict UTF-8 and executes the source.

```java
import io.simplebytecode.compiler.Runner;
import java.nio.file.Path;

new Runner(Path.of("Example.java")).run();
```

Runners implement `RunnerInterface`, which extends `Runnable`:

| Method | Purpose |
| --- | --- |
| `prepare(String src)` | Parses or compiles the source |
| `run()` | Executes the prepared program |
| `loadFile(Path)` | Reads a file with `FileUtil` and wraps failures |

Implementations must expose a public no-argument constructor because `Runner`
instantiates them reflectively.

## Packages

| Package | Content |
| --- | --- |
| `io.simplebytecode.compiler` | `Runner`, `RunnerInterface`, command-line entry point |
| `io.simplebytecode.compiler.classloader` | In-memory Java compilation and `main` invocation |
| `io.simplebytecode.compiler.groovy` | Groovy shell execution |
| `io.simplebytecode.compiler.kotlin` | Kotlin JSR-223 script execution |
| `io.simplebytecode.compiler.utils` | UTF-8 reading and extension matching |

## In-memory Java compilation

`InMemoryJavaCompiler` uses the system `JavaCompiler` and keeps generated class
files in a `MemoryClassLoader`. `JavaRunner` uses it to invoke the program's
`main` method, and `JavaSourceClassName` detects the public class name that must
be compiled. No class files are written to disk.

## Supported extensions

| Language | Extensions |
| --- | --- |
| Java | `.java`, `.jav` |
| Kotlin | `.kt`, `.kts` |
| Groovy | `.groovy`, `.gvy`, `.gy`, `.gsh` |

The IDE adds the compiled languages C, C++, C#, Go and Rust through external
toolchains; for those, see [Languages](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Languages) and the
[language reference](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Languages).

## Threading and resources

The compiler module does not create threads and does not start the IDE thread
manager. A runner executes on the caller's thread, so run long programs on a
managed background pool when embedding it in a Swing application. The Groovy and
Kotlin runners load their language runtimes into the current JVM; they cannot be
unloaded, so avoid repeatedly switching versions in one process.

## Testing

The module is covered by `RunnerTest`, `RunnerInterfaceTest`,
`SimpleBytecodeCompilerTest`, `InMemoryJavaCompiler` usage through `JavaRunnerTest`,
`JavaSourceClassNameTest`, `GroovyRunnerTest`, `KotlinRunnerTest`,
`FileUtilTest` and `MatchExtensionTest` under
`simplebytecode-compiler/src/test/java`.
