# Languages API

`io.simpleide.api.Languages` provides language discovery and managed process
execution. A language is available only when its external toolchain is on the
system `PATH`, except for the JVM languages that run through the bundled
bytecode compiler.

## Supported languages

| Language | Extensions | Requirement |
| --- | --- | --- |
| Java | `.java`, `.jav` | JDK on `PATH` or `JAVA_HOME` |
| Kotlin | `.kt`, `.kts` | Kotlin compiler on `PATH` |
| Groovy | `.groovy`, `.gvy`, `.gy`, `.gsh` | Groovy on `PATH` |
| JavaScript | `.js`, `.jsx`, `.mjs`, `.cjs`, `.jscript` | Node.js on `PATH` |
| Python 3 | `.py`, `.pyi`, `.pyw`, `.py3` | `python3` on `PATH` |
| C | `.c`, `.h` | C compiler on `PATH` |
| C++ | `.cpp`, `.hpp`, `.cc`, `.cxx`, `.hxx` | C++ compiler on `PATH` |
| C# | `.cs`, `.csx` | Mono compiler/runtime on `PATH` |
| Go | `.go` | Go toolchain on `PATH` |
| Rust | `.rs` | `rustc` on `PATH` |

See [Languages reference](../reference/LANGUAGES.md) for the exact commands and the
toolchain discovery rules.

## Discover what is available

```java
import io.simpleide.api.Languages;
import io.simpleide.language.config.LangConfig;
import io.simpleide.language.config.LangRefer;
import java.nio.file.Path;

LangConfig config = Languages.installAvailable();
for (var entry : config.getLanguages().entrySet()) {
  String extension = entry.getKey();
  LangRefer refer = entry.getValue();
  System.out.println(extension + " -> " + refer.getLangClass().getName());
}

boolean canRunPython = Languages.isAvailable(Path.of("hello.py"));
```

`installAvailable()` runs toolchain discovery once per JVM. It is safe to call
repeatedly. If a tool is installed later, restart the JVM to pick it up.

## Extension matching

`Languages.extensionOf(path)` returns the lower-case extension including the
dot:

| Path | Result |
| --- | --- |
| `Main.java` | `.java` |
| `archive.tar.gz` | `.gz` |
| `Makefile` | `""` |
| `script.PY` | `.py` |

`Languages.isAvailable` looks up that extension in the shared configuration.
There is no content-based detection; a header file `.h` is treated as C.

## List every supported extension

```java
for (String extension : Languages.supportedExtensions()) {
    System.out.println(extension); // .java, .py, .rs, ...
}
```

`supportedExtensions()` triggers the one-time toolchain discovery and returns an
unmodifiable set of every extension registered by an installed language. The
set is empty when no toolchain is available. Use it to validate file filters or
to decide which languages to advertise in your own UI.

## Create a language without starting it

```java
import io.simpleide.api.Languages;
import io.simpleide.language.AbstractLanguage;
import java.nio.file.Path;

AbstractLanguage language = Languages.create(Path.of("Main.java"));
System.out.println(language.getLangFriendly());
```

`create` throws `IllegalArgumentException` when no installed language supports
the extension. The language object is not running yet.

## Start a language

```java
import io.simpleide.api.Languages;
import io.simpleide.language.AbstractLanguage;
import java.nio.file.Path;

AbstractLanguage language = Languages.start(Path.of("Main.java"));
```

`start`:

1. Installs available languages if this is the first call.
2. Creates the language instance.
3. Registers the process node with `ThreadMaster`.
4. Returns immediately; the process runs on a background worker.

Use the returned object with `stop`, or keep it to inspect its state. A language
instance allows only one live process at a time; starting it again while the
previous process is alive throws `ProcessAlreadyExistsException`.

## Read output

Program output is line-based and shared through `Output`:

```java
import io.simpleide.language.io.Output;

String line;
while ((line = Output.getInstance().get(10)) != null) {
  System.out.println(line);
}
```

`get(waitSeconds)` polls the queue and returns `null` when the timeout expires.
It throws `InterruptedException` if the calling thread is interrupted.

The queue is global. If several programs run at once, their output lines are
interleaved. Use one interactive program at a time unless you control all
producers.

Output includes merged standard output and standard error because the process
redirects error into the output stream, and the language runner returns a
non-zero exit as an error.

## Send input

```java
import io.simpleide.language.io.Input;

Input.getInstance().set("Ada");
```

Input is line-based. A background forwarder reads the shared `Input` queue and
writes each line to the process standard input. The queue is global, so only one
interactive program should read input at a time.

## Stop a language

```java
import io.simpleide.api.Languages;

Languages.stop(language);
```

`stop` calls `forceStop()`:

- Marks the process as manually stopped.
- Calls `destroyForcibly()`.
- Waits up to three seconds for termination.
- Throws `ProcessFatalException` if the process does not exit in time.

The input forwarder is removed when the process exits.

## Custom languages

You can register your own `AbstractLanguage` subclass in the shared
configuration:

```java
import io.simpleide.api.Languages;
import io.simpleide.language.AbstractLanguage;
import java.nio.file.Path;

public final class MyLanguage extends AbstractLanguage {
  public MyLanguage(Path file) {
    pb = new ProcessBuilder("mytool", file.toString());
    langFriendly = "My Language";
    langConstant = "my-language";
  }

  @Override
  public void run() {
    super.run();
  }
}

Languages.config().addLanguage(MyLanguage.class, ".my", ".mylang");
```

Requirements:

- A public constructor taking `Path`.
- A configured `ProcessBuilder` (`pb`) or `processSteps`.
- Optional overrides for `handleExit`, `onExecutionFinished` or the process
  builders.
- The class must be on the classpath of the embedding application.

Registration order matters only in that an extension maps to the last registered
class. Registering after `installAvailable()` does not remove built-in languages.

## Multi-step processes

A language can define several process builders. `AbstractLanguage` runs them in
order and stops at the first non-zero exit code, except that the final process
always reaches the exit handler. This is how compile-then-run languages work.
Inspect a concrete language such as `JavaLanguage` or `GoLanguage` before
implementing your own.

## Lifecycle and threading

- `Languages.start` returns before the process finishes.
- `AbstractLanguage.run` executes on a shared worker thread.
- The process output scanner blocks that worker until the process exits.
- Starting many languages at once consumes the shared worker pool. Use one
  background task per program and avoid blocking the pool further.
- `forceStop` can be called from any thread.

## Errors

| Exception | Cause |
| --- | --- |
| `IllegalArgumentException` | No installed language supports the extension |
| `ProcessAlreadyExistsException` | The same language instance already has a live process |
| `ProcessFatalException` | Startup failure, interrupt, or a process that will not stop |
| `RejectedExecutionException` | The shared worker queue is full |

Catch `RuntimeException` around `Languages.start` in a UI action and show the
message to the user.

## Full example

```java
import io.simpleide.api.Languages;
import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;
import java.nio.file.Path;

public final class RunProgram {
  public static void main(String[] args) throws Exception {
    AbstractLanguage language = Languages.start(Path.of("greet.py"));

    Input.getInstance().set("Ada");

    while (true) {
      String line = Output.getInstance().get(5);
      if (line == null) {
        break;
      }
      System.out.println(line);
    }

    Languages.stop(language);
  }
}
```
