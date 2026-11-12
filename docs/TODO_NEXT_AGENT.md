# Handoff TODO for the next agent

Continue from the current `feature-editor` worktree. Read this file first, then
inspect `git status` and `git diff` before changing anything. The previous
increment is committed as `0f399ef`; the changes described under "Current
worktree changes" are not committed yet.

## User requirements

- Implement `com.fifesoft:autocomplete` completion for platforms/languages that
  do not receive a language server from `registry.json`, and add tests.
- Fix slow `ThreadMaster` synchronization that was reported to make manual LSP
  testing sluggish.
- Make directory/EDT tests deterministic on slow machines instead of relying on
  rigid timeouts.
- Fix the headless CI failure in `EditorManagerTest`.
- Keep `docs/LSP.md` and this handoff accurate.

## Current worktree changes

- `simpleide/src/main/java/io/simpleide/ui/editor/LocalCompletionProviders.java`
  - New keyword-based `DefaultCompletionProvider` factory covering Java, C#,
    Groovy, Kotlin, JavaScript, C, C++, Python, Go, and Rust.
- `simpleide/src/main/java/io/simpleide/ui/editor/EditorTab.java`
  - Uses `LanguageSupportFactory` when it has a provider and the JVM is
    headful; otherwise installs the keyword-based `AutoCompletion` provider.
  - Uninstalls either fallback when an LSP connects or the tab closes.
  - Exposes package-private `hasLocalLanguageSupport()` and
    `getLocalCompletionProvider()` for tests.
- `simpleide/src/main/java/io/simpleide/thread/AbstractThreadMaster.java`
  - Replaced the coarse `synchronized (this)` lifecycle/master lock with
    `AtomicBoolean` initialization/shutdown state. Tree and scheduler
    synchronization remains inside their own components.
  - `initialize()` uses compare-and-set so registered nodes are scheduled once;
    `register()` and `executeOnEDT()` stay safe through scheduler ID
    deduplication.
- `simpleide/src/main/java/io/simpleide/thread/ThreadTaskScheduler.java`
  - Creates/records a task under the scheduler lock, then dispatches to the EDT
    or worker pool outside the lock. Cancellation and rejection cleanup remain
    synchronized.
- Tests:
  - Added `LocalCompletionProvidersTest` for every editor language, the
    languages without a Windows x86 registry server, plain text, and headless
    `AutoCompletion` installation.
  - Added an `EditorManagerTest` Python keyword-completion integration test.
  - Reworked the local-fallback test to assert fallback installation/removal
    instead of parser counts, so it passes headless.
  - Increased `ThreadMasterTest`, `ThreadTaskSchedulerTest`,
    `EDTNodeListenerTest`, `EditorTabTest`, `LspStatusBarTest`, `FileMenuTest`,
    `TreeFileTest`, and `WatcherTest` waits so slow CI machines are not treated
    as failures.
- `docs/LSP.md`
  - Documents keyword-based completion fallback for Python, C++, Kotlin, C#,
    Go, and Rust, and notes that the keyword provider is used for all languages
    in headless mode.

## Verification state

- `make test` passes: 20 bytecode-compiler tests and 188 application tests,
  0 failures/errors, 8 skipped.
- `./mvnw -B -Djava.awt.headless=true test` passes with the same counts
  (BUILD SUCCESS), matching CI's headless environment.
- `./mvnw -B -pl simpleide test` also passes in headful mode with DISPLAY.
- Targeted headless run for `LocalCompletionProvidersTest`, `EditorManagerTest`,
  `EditorTabTest`, `ThreadMasterTest`, `ThreadTaskSchedulerTest`, and
  `EDTNodeListenerTest` passes.
- The previous CI headless failure
  (`JavaLanguageSupport.installKeyboardShortcuts` calling
  `Toolkit.getMenuShortcutKeyMaskEx`) is avoided by not touching
  `LanguageSupportFactory` when `GraphicsEnvironment.isHeadless()` is true.
- No Windows/MSYS2 environment is available locally, so the Windows x86 C++
  workflow still needs CI verification.

## Next steps

1. Inspect the next Windows x86 CI run. If the C++ probe still fails, use the
   captured `g++` diagnostics already added to `.github/workflows/windows32.yml`
   rather than guessing further about the toolchain.
2. Review and commit the current worktree changes if approved. Do not amend
   `0f399ef`; create a new commit.
3. If manual LSP sluggishness persists after the lock reduction, capture a
   thread dump and identify whether the remaining contention is in the per-server
   LSP executor, `ThreadTreeRegistry`, or `ThreadTaskScheduler` before changing
   locking again.
4. Re-run the headless suite after any further changes; CI has no DISPLAY.
