# TODO for the next AI agent

Status: work in progress. The three error reports are `ERROR1.txt`, `ERROR2.txt`, and `ERROR3.txt`
in the repository root. Do **not** run `make test` until the safety checklist below passes.

## What was already changed

### ERROR3 — dangerous kill / TTY / PTY (main blocker)

`simpleide/src/main/java/io/simpleide/ui/terminal/ProcessTreeTerminator.java` was rewritten:

- Removed every external kill path: `pkill -KILL -P`, `/bin/kill -KILL`, `ps` process-group
  lookup, process-group kills, and `taskkill`.
- Only captured `ProcessHandle` instances are signalled, deepest-first.
- Hard guards refuse to signal PID 1, the current JVM, and all ancestors of the current JVM.
- Added `ProcessTreeTerminator.isSafeToTerminate(ProcessHandle)` for tests.
- Added a regression test
  `ProcessTreeTerminatorTest.neverTerminatesPidOneTheCurrentJvmOrItsAncestors`.
- `BusyBoxTerminalSessionTest` macOS-only PTY tests now require
  `LangPlatform.getCurrentPlatform() == MACOSAARCH64`; they no longer run the macOS path on Linux.

This is the reason the host desktop session was being killed: a reparented child reported parent
PID 1, and the old code ran `pkill -KILL -P 1`.

### ERROR1 — jdtls exits with code 13, RSTA cannot find `java.lang.Object`

- `simpleide/src/main/resources/io/simpleide/lsp/dowload/registry.json`:
  - `jdtls-java21` (1.63.0) was renamed to `jdtls-java25` and its `minimumVersion` is now `25`.
  - jdtls 1.63.0 requires Java 25; jdtls 1.43.0 requires Java 17. Java 17/21 now select 1.43.0.
- `simpleide/src/test/java/io/simpleide/lsp/dowload/LspManagerTest.java` updated for the new
  version mapping and now also checks that Java 25 selects `jdtls-java25`.
- `simpleide/src/main/java/io/simpleide/ui/editor/EditorTab.java` now calls
  `JarManager.addCurrentJreClassFileSource()` once before registering local `text/java` language
  support, so RSTA can resolve `java.lang.Object`.

### ERROR2 — macOS ARM64 CI / Java 25 terminal tests

Partial mitigation only:

- `BusyBoxTerminalSession.close()` now skips a second graceful shutdown when the session was
  already terminated (this removes repeated 2-second waits that could hit the 30-second test
  timeout).
- `close(boolean force)` uses immediate forced termination when a shutdown was already requested.
- The uncommitted `simpleide/pom.xml` state (direct `org.jetbrains.pty4j:pty4j:0.13.13` dependency,
  `pty4j21` macOS ARM64 profiles removed) was left as found. Decide explicitly whether to keep it.
  ERROR2 may still need a macOS/Java-25-specific fix that cannot be reproduced on Linux.

## Build status when this handoff was written

- `./mvnw -B -DskipTests package` compiled all main and test sources successfully.
- The full package goal was interrupted by the user while the assembly jar was being written, so
  the jar/package step itself has not been confirmed end-to-end.
- No Maven/Surefire processes were left running.

## Safety checklist before running tests

1. Confirm there are no leftover terminal/test processes:
   `ps -eo pid,ppid,stat,etime,cmd | grep -E 'mvn|maven|surefire|simpleide' | grep -v grep`
2. Confirm `ProcessTreeTerminator` contains no external kill command:
   `grep -nE 'pkill|/bin/kill|taskkill|ProcessBuilder' simpleide/src/main/java/io/simpleide/ui/terminal/ProcessTreeTerminator.java`
   (expected: no matches)
3. Run only focused, safe tests first:
   `./mvnw -B -pl simpleide -Dtest=ProcessTreeTerminatorTest test`
   `./mvnw -B -pl simpleide -Dtest=BusyBoxTerminalSessionTest test`
   `./mvnw -B -pl simpleide -Dtest=LspManagerTest test`
4. Only after those pass, run `make test`.

## Remaining work

1. Re-run the focused tests above on Linux and fix any regression.
2. Run `make test` on Linux and capture the full Surefire output.
3. Verify ERROR1 in the running IDE:
   - On Java 21, the log must show jdtls 1.43.0 being selected (not 1.63.0), and no
     `Language server exited before initialization (code 13)`.
   - Opening a Java file must not log `Couldn't find ClassFile for: java.lang.Object`.
4. Verify ERROR2 on macOS ARM64 with Java 17, 21, 25 and 27 (`.github/workflows/macos-arm.yml`):
   - `BusyBoxTerminalSessionTest` must finish under its `@Timeout(30s)` methods.
   - PTY processes may need a longer reap/observation window on macOS; do not reintroduce
     process-group or parent-PID kills to make tests pass.
   - If the BusyBox ARM64 binary keeps exiting with code 133 on macOS 26, keep the `/bin/sh`
     fallback behavior but make tests assert the fallback path rather than BusyBox-specific state.
5. Decide whether to keep the `pty4j` vs `pty4j21` POM change. Validate on macOS ARM64 before
   committing.

## Hard rules for the next agent

- Never signal PID 1 or an ancestor of the JVM.
- Never use `pkill`, `kill -P`, process-group kills, or PID values derived only from a parent link.
- Never run `make test` while the safety checklist or focused tests fail.
- Keep the existing code style and do not revert unrelated user changes in `App.java` or
  `simpleide/pom.xml` without asking.
