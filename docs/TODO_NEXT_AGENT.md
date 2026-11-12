# Handoff TODO for the next agent

Continue the interrupted LSP, thread-lifecycle, and Windows x86 C++ CI work. Read this file first, then inspect the current worktree and preserve its changes. The previous batch was committed as `fix ci 11`; the follow-up fixes below are uncommitted.

## User requirements

- Fix the Windows 32-bit CI failure where `g++ -dumpmachine` reports `i686-w64-mingw32` but compiling a C++ probe fails.
- Remove Java, JavaScript, and Python language servers from Windows x86.
- Use RSyntaxTextArea's own local language support as a fallback when an LSP is unavailable or fails; document its limitations accurately.
- Add integration coverage and improve LSP startup/thread performance without breaking thread-tree lifecycle tracking.
- Investigate reports that CI thread-tree tests see an extra/missing node and that manual LSP tests sometimes download or start the wrong server/version.
- Investigate the Java 21/Linux x64 LSP/thread issue. The available CI evidence described below identifies one thread-test race, not a confirmed Java 21 LSP startup failure.

## Current worktree changes

- `.github/workflows/windows32.yml`
  - Changed MSYS2 installation from `mingw-w64-i686-gcc` to the `mingw-w64-i686-toolchain` group.
  - Reworked the C++ probe source generation to ASCII and captured compiler output; on failure it prints compiler identity and C++ include search diagnostics.
  - The actual cause of the original compiler failure is still unknown. The old CI log only showed the generic failure and contained no compiler diagnostic.
- `simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json`
  - Removed `jj-language-server-x86-windows`.
  - Removed x86 from the Windows JavaScript and Python entries.
  - Preserved Windows x86 C# coverage; verify it remains present.
- `simpleide/src/main/java/io/simpleide/ui/editor/EditorManager.java` and `EditorTab.java`
  - Activate `LanguageSupportFactory` local support while connecting and fall back on null/error; remove local support when a real LSP connects or a tab closes.
  - The library is not an LSP and does not provide equivalent support for every language; it has no Python support.
- `simpleide/src/main/java/io/simpleide/lsp/client/LspClient.java`
  - Moved protocol work to a dedicated two-thread daemon executor and stderr reading to a dedicated daemon thread, with shutdown cleanup.
  - Verify process-exit, initialization timeout, and close races; ensure dedicated threads do not leak.
- `simpleide/src/main/java/io/simpleide/lsp/dowload/LspReleaseResolver.java`
  - Added an in-memory cache: successful release metadata for six hours and failed lookups for two minutes, with per-server refresh locking.
  - Validate cache correctness and concurrency; the goal is to avoid blocking network checks per tab while still allowing updates.
- `simpleide/src/main/java/io/simpleide/lsp/dowload/LspManager.java`
  - Selection still refreshes release metadata before checking/installing the selected version.
- `simpleide/src/test/java/io/simpleide/lsp/dowload/LspManagerTest.java`
  - Updated expected Windows x86 coverage and added Java 21/JDT LS selection coverage for Linux x64.
- `simpleide/src/test/java/io/simpleide/ui/editor/EditorManagerTest.java`
  - Added a no-server local-fallback test, including parser cleanup on close.
- `simpleide/src/test/java/io/simpleide/thread/nodes/edt/listeners/EDTNodeListenerTest.java`
  - Changed the document-listener tree cleanup assertion to wait for asynchronous task removal.
- `docs/LSP.md`
  - Updated coverage, fallback behavior, and thread/cache lifecycle documentation.

## Follow-up fixes (uncommitted)

- Removed `x86` from the Windows Python entry in `registry.json`; Windows x86
  now has no Java, JavaScript, or Python language server.
- Removed a duplicate `@Test` that prevented `LspManagerTest` from compiling.
- Fixed `EditorManagerTest` to compare parser counts against the editor's
  baseline: code folding also registers a parser, so the local language
  support parser is removed but the folding parser remains after close.
- Documented the actual bundled `languagesupport` 3.4.1 fallback coverage in
  `docs/LSP.md` (Java, JavaScript, Groovy, and C among editor-mapped types;
  no Python, C++, Kotlin, C#, Go, or Rust fallback).

## Verification state

- `make test` passes locally: 20 bytecode-compiler tests and 183 application
  tests, 0 failures, 8 skipped.
- The targeted command
  `./mvnw -pl simpleide -am -Dtest=EditorManagerTest,LspManagerTest,EDTNodeListenerTest -Dsurefire.failIfNoSpecifiedTests=false test`
  passes 22 tests.
- `registry.json` parses as JSON and `.github/workflows/windows32.yml` parses
  as YAML.
- The previous Java 21/Linux x64 CI failure was only the thread-tree assertion
  in `EDTNodeListenerTest.documentNodeDispatchesEdtChangesThroughThreadMaster`
  observing 26 instead of 25. The test now waits for asynchronous task removal.
- No Windows/MSYS2 environment is available locally, so the C++ workflow change
  still needs CI verification.
- The Java 21/Linux x64 LSP selection is covered by `LspManagerTest`; no
  confirmed Java 21 LSP startup failure was reproduced.

## Next steps

1. Inspect the next Windows x86 CI run and use the captured `g++` diagnostics
   if the C++ probe still fails; do not guess further about the toolchain.
2. Commit the follow-up fixes after review.
3. If a wrong-server/version report recurs, capture the selected server id,
   resolved version, command, and install marker before changing selection.
4. Re-run targeted tests after any further changes and inspect the final diff,
   including the remaining Windows-only CI limitation.
