# Running Languages

Simple IDE implements Java, Kotlin, Groovy, JavaScript, Python, C#, C++, C,
Go, and Rust. A language is registered only when its required tools are
available. The language setup is initialized once at application startup:

```java
LangConfig config = LangConfigHolder.LANG_CONFIG;
new LangSetup(config).run();
```

`LangSetup` registers extensions for installed compilers and interpreters, plus
Java, Kotlin, and Groovy when the embedded bytecode compiler is present.
`LangConfig.getLanguage(extension)` returns the language class for a registered
extension, or `null` when the language or its required tools are unavailable.
Use `LangConfig.createLanguage(extension, sourcePath)` to construct an
available language with the source file's `Path`:

```java
Path source = Path.of("/projects/example/main.py");
String extension = source.getFileName().toString();
extension = extension.substring(extension.lastIndexOf('.')).toLowerCase(Locale.ROOT);

AbstractLanguage language = config.createLanguage(extension, source);
if (language == null) {
    throw new IllegalArgumentException("No installed language supports " + extension);
}
language.startManaged();
```

`startManaged()` registers the process with `ThreadMaster` and starts it asynchronously. For bytecode and interpreter languages, the source file is passed directly to the runtime. Compiler languages build into a temporary directory, run the result, and remove temporary artifacts when the process exits.

## Testing without language toolchains

The normal `make test` target runs language execution tests when their required tools are installed. Those tests are tagged `language-tools` and verify the configured compilers, interpreters, and runtimes. Use `make test-nolang` to run the test suite while excluding those tool-dependent execution tests; language configuration and process-lifecycle unit tests still run.

## Building the application

Run `make jar` to create the runnable FAT JAR at `simpleide/target/maven-mutation-testing-jar-with-dependencies.jar`. Its manifest sets `io.simpleide.App` as `Main-Class`, so it can be launched directly:

```sh
java -jar simpleide/target/maven-mutation-testing-jar-with-dependencies.jar
```

`make run` builds the same JAR and launches it.

## Input and output

The shared `Input` and `Output` queues are thread-safe. Send complete lines to a running process and read output with a timeout:

```java
Input.getInstance().set("user input");
String line = Output.getInstance().get(5);
```

`get(timeoutSeconds)` blocks only until a line arrives or the timeout expires, returning `null` on timeout. Read output from a `ThreadMaster` task rather than the Swing event-dispatch thread. The queues are shared across language processes, so applications should avoid running multiple interactive processes that consume the same input/output stream simultaneously.

## Supported language toolchains

| Language | Registered extensions | Implementation | Required tools |
| --- | --- | --- | --- |
| Java | `.java`, `.jav` | Embedded bytecode compiler | JDK and embedded Simple IDE bytecode compiler |
| Kotlin | `.kt`, `.kts` | Embedded bytecode compiler | JDK and embedded Simple IDE bytecode compiler |
| Groovy | `.groovy`, `.gvy`, `.gy`, `.gsh` | Embedded bytecode compiler | JDK and embedded Simple IDE bytecode compiler |
| JavaScript | `.js`, `.jsx`, `.mjs`, `.cjs`, `.jscript` | Interpreter | `node` |
| Python 3 | `.py`, `.pyi`, `.pyw`, `.py3` | Interpreter | `python3` on Linux/macOS; `python` on Windows |
| C# | `.cs`, `.csx` | Compiler and Mono runtime | `mcs`, `mono` |
| C++ | `.cpp`, `.hpp`, `.cc`, `.cxx`, `.hxx` | Compiler/linker | `g++` |
| C | `.c`, `.h` | Compiler/linker | `gcc` |
| Go | `.go` | Compiler | `go` |
| Rust | `.rs` | Compiler | `rustc`; Windows x86 also needs the i686 GNU target and `gcc` linker |

C and C++ are each compiled and linked directly by one `gcc` or `g++` command
into a temporary executable. Go uses `go build`; Rust uses `rustc`. C# is
compiled to a temporary executable by `mcs` and run with Mono. Build artifacts
are removed when the language process finishes.

For editor language-server features and per-platform LSP availability, see
[`LSP.md`](LSP.md).
