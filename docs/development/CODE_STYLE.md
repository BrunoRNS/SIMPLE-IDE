# Code style

Simple IDE uses Google Java Format through Spotless. The formatter is mandatory;
the conventions below cover what it does not enforce.

## Formatting

```sh
make format
make format-check
```

- Google Java Format 1.28.0.
- Two-space indentation.
- 100-column line limit where the formatter can honor it.
- Braces on the same line for control statements and methods.
- Imports are sorted and unused imports are removed by the formatter.
- One blank line between members; no blank line at the start or end of a block.
- Trailing whitespace is removed.

Do not hand-format around the tool. If the formatter produces an unexpected
diff, restore the file and rerun `make format` on the intended change only.

## Java version

- Source and target are Java 17.
- Use records for immutable data carriers.
- Use switch expressions for exhaustive mappings.
- Use `var` when the type is obvious from the right-hand side.
- Use text blocks for multi-line SQL, JSON or command fragments only when it
  improves readability.
- Do not use preview features.

## Naming

| Element | Convention | Example |
| --- | --- | --- |
| Package | Lowercase, singular | `io.simpleide.lsp.client` |
| Class | `UpperCamelCase` | `LanguageServerManager` |
| Interface | `UpperCamelCase`, often an adjective | `Runnable` |
| Method | `lowerCamelCase`, verb first | `startLanguageServer` |
| Variable | `lowerCamelCase` | `workspaceRoot` |
| Constant | `UPPER_SNAKE_CASE` | `MAX_QUEUED_TASKS` |
| Type parameter | Single uppercase letter | `T`, `E` |
| Test method | Behavior sentence | `startsServerForJavaDocument` |

Use domain names from the project: `TypeFile`, `LangConfig`, `LspServer`,
`ThreadMaster`, `BusyBoxTerminalController`. Do not introduce synonyms for the
same concept.

## Packages

- `io.simpleide.api` — stable facades for embedding.
- `io.simpleide.ui.*` — Swing components and application UI.
- `io.simpleide.language.*` — toolchain discovery and process execution.
- `io.simpleide.lsp.*` — LSP client, registry, installer and editor support.
- `io.simpleide.thread.*` — thread tree, nodes, schedulers and pools.
- `io.simpleide.utils.*` — small shared utilities.
- `io.simpleide.frame.*` — frames, popups and application wiring.

Keep dependency direction downward: `api` may use `ui`, `language`, `lsp` and
`thread`; `thread` must not depend on UI; `language` may depend on `thread` but
not on Swing; `lsp` may depend on `thread` and editor support where necessary.

## Classes and methods

- Prefer `final` classes for leaf implementations and facades.
- Keep constructors side-effect free unless the type is explicitly a session or
  controller. Starting processes belongs in `start()` or `connect()`.
- Return interfaces or facades from public APIs, not implementation types, when
  the caller does not need the implementation.
- Avoid returning `null`. Use `Optional`, an empty collection or a documented
  nullable return.
- Do not expose mutable collections. Return `Map.copyOf`, `List.copyOf` or an
  unmodifiable view.
- Validate arguments at the public boundary with `Objects.requireNonNull` and
  explicit `IllegalArgumentException`.
- Keep methods short enough to read without scrolling. Extract a private helper
  instead of adding a long comment.
- Do not add a boolean parameter that changes a method's behavior; use a
  separate method or an enum.

## Comments and Javadoc

- Do not add comments that restate the code.
- Add a comment only for a non-obvious constraint, workaround or protocol
  requirement.
- Public API classes and methods have Javadoc with `@param`, `@return` and
  `@throws` where applicable.
- Javadoc uses the imperative or descriptive present tense.
- Do not leave commented-out code.
- Do not use `TODO` without an issue reference or a concrete next step.
- Keep line comments above the code they explain, not at the end of a line,
  unless the line is a short annotation.

## Imports

- No wildcard imports.
- Import static members individually when used repeatedly; otherwise qualify.
- Do not import `java.awt.*` or `javax.swing.*`.
- Remove unused imports. Spotless does this automatically.
- Keep import groups in the formatter's order: static, then non-static, sorted.

## Strings and localization

- UI strings are currently literals in the application. Keep them consistent
  with adjacent code.
- Use `Locale.ROOT` for case conversion and formatting that must be stable
  across locales.
- Use `StandardCharsets.UTF_8` explicitly for file and process text.
- Do not concatenate untrusted text into shell commands; pass arguments to
  `ProcessBuilder` as separate elements.

## Error handling

- Catch the narrowest exception that can be handled.
- Never swallow an exception silently. Log it with context or rethrow with a
  cause.
- Use a domain exception (`ProcessFatalException`,
  `ProcessAlreadyExistsException`, `LspServerStartupException`) when the caller
  can act on it.
- Preserve the original cause: `new IllegalStateException("...", cause)`.
- Restore the interrupt flag when catching `InterruptedException`:
  `Thread.currentThread().interrupt()`.
- Do not use exceptions for normal control flow.
- Clean up resources in `finally` or try-with-resources.

## Threading

- Swing mutations run on the EDT. Use `ThreadMaster.executeOnEDT` or
  `Threads.onEdt`.
- Background work runs on a managed pool, not on `new Thread`.
- Do not block a shared worker while waiting for another shared task.
- Use `ManagedThreadPool` for work that can block on its own tasks.
- Shut down executors, clients and pools when the owner closes.
- Do not call `ThreadMaster.shutdown()` from a library.
- Make cancellation observable: check the interrupt flag in loops and do not
  catch `InterruptedException` without restoring it.

## Logging

- Use SLF4J: `private static final Logger LOGGER = LoggerFactory.getLogger(...)`.
- Log at `debug` for expected control flow, `info` for lifecycle changes,
  `warn` for recoverable problems and `error` for failures.
- Include identifiers (node id, document URI, server name) but not secrets or
  full user home paths.
- Do not log the contents of source files or program output at `info`.
- Use parameterized logging: `LOGGER.info("Starting {} for {}", name, path)`.
- Do not log and rethrow the same exception at the same level; choose one.

## Resource management

- Use try-with-resources for streams, archives, scanners and clients.
- Delete temporary files in a `finally` block or a close callback.
- Close process input/output streams explicitly when the protocol requires it.
- Do not rely on finalizers.
- Register a close hook only for resources that outlive the current method.

## Tests

- Test names describe behavior, not implementation.
- Use AssertJ for assertions.
- Use Awaitility for asynchronous conditions; avoid arbitrary `sleep`.
- Use `@TempDir` for filesystem tests.
- Use `@Tag("language-tools")` for tests that execute external toolchains.
- Do not depend on test execution order.
- Prefer one behavior per test. A test may have several assertions about the
  same behavior.
- Keep fixture files under `src/test/resources` and load them through the
  classpath.

## Swing

- Build component trees on the EDT.
- Use layout managers, not absolute positioning.
- Set accessible names on icon-only buttons.
- Prefer FlatLaf client properties to custom painting.
- Dispose windows and remove listeners when the owner is closed.
- Do not hold static references to editors or frames.
- Use `RTextScrollPane` for RSyntaxTextArea, not a plain `JScrollPane`, when the
  editor needs line numbers, folding or current-line highlighting.

## Documentation

- Write in English.
- Use relative links.
- Put a command on its own line in a fenced block.
- Prefer tables for reference data and numbered steps for procedures.
- Update the closest page and the index when adding a page.
- Do not duplicate long explanations; link instead.
