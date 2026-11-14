# Testing

Simple IDE uses JUnit 5, AssertJ and Awaitility with Maven Surefire.

## Commands

```sh
make test          # full suite
make test-nolang   # full suite without tests tagged language-tools
make verify        # format check, full suite and fat JAR
```

Direct Maven:

```sh
./mvnw test
./mvnw -DexcludedGroups=language-tools test
./mvnw -Dtest=ThreadMasterTest test
./mvnw -Dtest=ThreadMasterTest#rejectsTaskAfterShutdown test
./mvnw -pl simpleide -am test
```

The build uses JDK 17+, with CI covering 17, 21, 25 and 27. `simpleide` configures
Surefire with `--enable-native-access=ALL-UNNAMED` for JNA/pty4j on newer JDKs.

## Test layout

```text
simpleide/src/test/java/io/simpleide/
├── frame/          Log panel, log appender and popups
├── language/       Toolchain discovery, process execution, I/O
├── lsp/            Client shutdown, completion, diagnostics, registry
├── thread/         Thread tree, scheduler, nodes, validation
├── ui/             Editor, tabs, themes, terminal, file tree
└── utils/          Resource and file services
```

Test resources are under `simpleide/src/test/resources`. Prefer classpath
resources over hard-coded absolute paths.

## Tags

Tests that execute real external toolchains are tagged:

```java
@Tag("language-tools")
```

Use `make test-nolang` to exclude them. They cover:

- C and C++ compilation and execution.
- Go build and run.
- Rust compilation and execution.
- C# through Mono.
- Node.js and Python interpreters.
- Java, Kotlin and Groovy bytecode paths.

CI installs the toolchains and runs the full suite. A local machine without a
toolchain can still run everything else.

## Test categories

### Unit tests

- No network, no external toolchain.
- Use `@TempDir` for files.
- Use fake processes or small scripts for process behavior.
- Assert on observable results, not private fields.

### Asynchronous tests

Use Awaitility instead of `Thread.sleep`:

```java
await()
    .atMost(Duration.ofSeconds(5))
    .untilAsserted(() -> assertThat(node.isRunning()).isFalse());
```

For `CompletableFuture`, prefer `get(timeout, unit)` or `orTimeout` over an
unbounded `join`. Always clean up managers and pools in `@AfterEach`.

### Process tests

- Use a script or a small Java program as the child process.
- Assert on exit codes, output lines and termination.
- Always destroy the process in cleanup, even when an assertion fails.
- Do not depend on shell syntax that differs between Linux, macOS and Windows;
  use a JVM-based child process when possible.

### Swing tests

- Create and mutate components on the EDT with
  `SwingUtilities.invokeAndWait`.
- Do not show a `JFrame` unless the test requires a real window.
- Use `assertThat(component).isNotNull()` and inspect model state.
- Dispose windows in `@AfterEach`.
- CI may run without a display for some workflows; avoid tests that require a
  visible window if a model-level assertion is enough.

### Terminal tests

- `BusyBoxPlatformTest` covers platform paths, fallback and extraction.
- `BusyBoxTerminalSessionTest` covers process lifecycle.
- `BusyBoxTerminalControllerTest` covers tab limits and close behavior.
- These tests extract real binaries and need the matching platform resource.

### LSP tests

- Registry and version selection tests do not start a server.
- Client shutdown tests use a controlled process.
- Completion and diagnostic tests use fixtures.
- Tests must not download servers from the network. If a test needs an installed
  server, mark it `language-tools` and skip when unavailable.

## Test naming

Use behavior-oriented names:

```java
@Test
void rejectsNewTaskAfterShutdown() {}

@Test
void selectsClosestCompatibleServer() {}

@Test
void fallsBackToX86OnOldWindows() {}
```

Avoid names that describe implementation details (`testRegister2`,
`verifyPrivateField`).

## Assertions

Use AssertJ:

```java
assertThat(result).isEqualTo(expected);
assertThat(list).containsExactly("a", "b");
assertThatThrownBy(() -> Languages.start(path))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("No installed language");
```

Prefer a specific assertion over a generic `isTrue()`. Include a reason when the
failure message would otherwise be unclear.

## Fixtures

- Keep small source files under `src/test/resources`.
- Use `@TempDir` for files written by the test.
- Do not write into the repository during a test.
- Clean temporary directories through JUnit, not a manual `deleteOnExit` chain.

## Coverage and mutation testing

The parent POM configures PIT with the JUnit 5 plugin. To run mutation coverage
manually:

```sh
./mvnw -pl simpleide pitest:mutationCoverage
```

Reports are written under `simpleide/target/pit-reports/`. Mutation testing is
not part of `make test` because it is slow. Use it when changing core logic, not
for every documentation or UI change.

## Reports

Surefire reports:

```text
simpleide/target/surefire-reports/
```

Read the `.txt` or `.xml` file for a failed test. The Maven output includes the
failing assertion and stack trace.

## Debugging failures

1. Reproduce with a single test:
   `./mvnw -Dtest=ClassName#method test`.
2. Add `-DfailIfNoTests=false` when selecting tests across modules.
3. Check for leftover processes with `jps -l` or the OS process list.
4. For flaky async tests, replace sleeps with Awaitility and add cleanup.
5. For a failed language test, run the same command manually with the fixture
   file and compare output.
6. For Windows-only failures, check path separators, executable extensions and
   line endings.

## CI expectations

A pull request should pass:

- Linux x64 on JDK 17, 21, 25 and 27.
- Windows x64.
- Linux ARM64.
- macOS Intel and Apple Silicon.
- Windows x86 and ARM64 where supported.
- The packaging validation step on the first JDK in each matrix.

The release workflow additionally packages artifacts for every supported
OS/architecture. A packaging failure is a release blocker even when tests pass.
