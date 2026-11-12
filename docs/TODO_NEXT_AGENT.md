# Handoff TODO for the next agent

Continue the interrupted LSP, thread-lifecycle, and Windows x86 C++ CI work. Read this file first, then inspect the current worktree and preserve its changes. Nothing has been committed.

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

## Verification state

- The previous Java 21/Linux x64 CI run (`43255176533`, job `131311331274`) failed only in `EDTNodeListenerTest.documentNodeDispatchesEdtChangesThroughThreadMaster`: it asserted the global thread-tree size immediately after a latch and observed 26 instead of 25. This is consistent with the EDT task being removed just after the latch; the test now awaits cleanup.
- The first local targeted Maven attempt compiled upstream modules but failed to compile `EditorManager` because the new `LspClient` type import was missing. That import was added.
- After the missing import was fixed, the targeted run was started twice, most recently with:
  `./mvnw -pl simpleide -am -Dtest=EditorManagerTest,LspManagerTest,EDTNodeListenerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Both runs were stopped at the user's request before a result was available. Resume with this targeted command; do not claim tests passed. `make test` has not been run.
- No Windows/MSYS2 environment is available locally, so the C++ workflow change still needs CI verification.

## Next steps

1. Inspect `git status` and the complete diff; check for accidental or conflicting edits.
2. Run the targeted Maven test command above. Resolve compile/test failures, especially the new fallback parser assertion and asynchronous thread-tree cleanup.
3. Add or adjust focused tests for release-cache concurrency and server selection if needed. Recheck that simulated Windows x86 properties do not affect unrelated tests.
4. Review the server-selection/install path end to end for the reported wrong-server/version case. Verify Java 21 selects `jdtls-java21` on Linux x64 and that startup errors cannot silently select a different server.
5. Confirm local fallback activation/unregistration and available language coverage against the actual RSyntaxTextArea library; keep Python's limitation explicit.
6. Validate workflow YAML and inspect the next Windows x86 CI run for the captured `g++` diagnostics. If it still fails, use those diagnostics to fix the toolchain rather than guessing.
7. Review thread shutdown paths for leaked processes/threads and ensure `ThreadMaster` tree nodes are removed deterministically.
8. Re-run the targeted tests after fixes, inspect the final diff, and summarize verified results and any remaining Windows-only CI limitation.
