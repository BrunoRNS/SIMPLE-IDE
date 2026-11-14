package io.simpleide.ui.terminal;

import io.simpleide.language.config.LangPlatform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terminates a terminal process and its descendants.
 *
 * <p>The implementation deliberately never shells out to {@code kill}, {@code pkill} or any
 * process-group helper. Once a child is reparented (for example to PID 1) the operating system may
 * reuse or lose that relationship, and signalling such a PID can kill unrelated host processes.
 * Only {@link ProcessHandle} instances that were captured while they were still descendants of the
 * terminal process are signalled, deepest-first. As an additional safety net, PID 1, the current
 * JVM and all of its ancestors are never signalled.
 */
public final class ProcessTreeTerminator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTreeTerminator.class);
  private static final int MAX_TREE_SWEEPS = 3;
  private static final int MAX_ANCESTOR_DEPTH = 128;
  private static final long DESCENDANT_EXIT_TIMEOUT_MILLIS = 2_000;

  private ProcessTreeTerminator() {}

  static void terminate(Process process) {
    terminate(process, isMacArm64());
  }

  static void terminate(Process process, LangPlatform platform) {
    terminate(process, platform == LangPlatform.MACOSAARCH64);
  }

  private static void terminate(Process process, boolean macArm64) {
    if (process == null) {
      return;
    }

    ProcessHandle root = findHandle(process.pid()).orElse(null);
    List<ProcessHandle> descendants = captureDescendants(process);
    terminateTree(process, root, descendants, macArm64);
  }

  /**
   * Captures the descendants of a live process. Captured handles are safe to terminate later even
   * if the operating system reparents them in the meantime.
   */
  public static List<ProcessHandle> captureDescendants(Process process) {
    if (process == null) {
      return List.of();
    }
    Optional<ProcessHandle> root;
    try {
      root = ProcessHandle.of(process.pid());
    } catch (RuntimeException exception) {
      LOGGER.warn("Unable to find terminal process {} before shutdown.", process.pid(), exception);
      return List.of();
    }
    if (root.isEmpty()) {
      return List.of();
    }
    try (var handles = root.get().descendants()) {
      return handles.toList();
    } catch (RuntimeException exception) {
      LOGGER.warn("Unable to capture terminal child processes before shutdown.", exception);
      return List.of();
    }
  }

  public static void terminateCapturedDescendants(List<ProcessHandle> descendants) {
    terminateCapturedDescendants(descendants, isMacArm64());
  }

  public static void terminateCapturedDescendants(
      List<ProcessHandle> descendants, LangPlatform platform) {
    terminateCapturedDescendants(descendants, platform == LangPlatform.MACOSAARCH64);
  }

  private static void terminateCapturedDescendants(
      List<ProcessHandle> descendants, boolean macArm64) {
    if (descendants == null || descendants.isEmpty()) {
      return;
    }
    List<ProcessHandle> ordered = deepestFirst(descendants);
    Set<Long> signalledPids = new HashSet<>();
    for (ProcessHandle descendant : ordered) {
      terminate(descendant, signalledPids);
    }
    awaitCapturedDescendants(ordered);
    if (macArm64) {
      // PTY-backed processes can need a second signal while their reaper thread catches up.
      for (ProcessHandle descendant : ordered) {
        if (descendant.isAlive()) {
          terminate(descendant, new HashSet<>());
        }
      }
      awaitCapturedDescendants(ordered);
    }
  }

  static void terminateMacArm64(Process process) {
    terminate(process, true);
  }

  static void terminateMacArm64Descendants(List<ProcessHandle> descendants) {
    terminateMacArm64Descendants(null, descendants);
  }

  /**
   * Stops only the supplied descendants (never the root shell) so a graceful close can still send
   * EOF to the terminal afterwards.
   */
  static void terminateMacArm64Descendants(Process root, List<ProcessHandle> descendants) {
    if (descendants == null || descendants.isEmpty()) {
      return;
    }
    List<ProcessHandle> ordered = deepestFirst(descendants);
    Set<Long> signalledPids = new HashSet<>();
    for (ProcessHandle descendant : ordered) {
      terminate(descendant, signalledPids);
    }
    awaitCapturedDescendants(ordered);
    for (ProcessHandle descendant : ordered) {
      if (descendant.isAlive()) {
        terminate(descendant, new HashSet<>());
      }
    }
    awaitCapturedDescendants(ordered);
  }

  private static void terminateTree(
      Process process, ProcessHandle root, List<ProcessHandle> descendants, boolean macArm64) {
    List<ProcessHandle> ordered = deepestFirst(descendants);
    Set<Long> signalledPids = new HashSet<>();

    // Kill children before their parents, otherwise the children are reparented and may escape.
    for (ProcessHandle descendant : ordered) {
      terminate(descendant, signalledPids);
    }
    if (root != null) {
      terminate(root, signalledPids);
    }
    terminateRoot(process);

    for (int sweep = 0; sweep < MAX_TREE_SWEEPS; sweep++) {
      List<ProcessHandle> remaining =
          ordered.stream()
              .filter(ProcessHandle::isAlive)
              .filter(ProcessTreeTerminator::isSafeToTerminate)
              .toList();
      if (remaining.isEmpty() && !isRootAlive(root, process)) {
        return;
      }
      for (ProcessHandle remainingProcess : remaining) {
        terminate(remainingProcess, new HashSet<>());
      }
      if (isRootAlive(root, process)) {
        terminateRoot(process);
      }
      if (sweep + 1 < MAX_TREE_SWEEPS) {
        awaitCapturedDescendants(ordered);
      }
    }

    if (macArm64) {
      LOGGER.debug(
          "macOS ARM64 terminal shutdown finished after {} sweeps; rootPid={}, rootAlive={}, "
              + "descendants={}.",
          MAX_TREE_SWEEPS,
          process.pid(),
          process.isAlive(),
          ordered.stream().map(ProcessTreeTerminator::describeProcess).toList());
    }
  }

  private static boolean isRootAlive(ProcessHandle root, Process process) {
    if (root != null && root.isAlive()) {
      return true;
    }
    try {
      return process.isAlive();
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private static Optional<ProcessHandle> findHandle(long pid) {
    try {
      return ProcessHandle.of(pid);
    } catch (RuntimeException exception) {
      LOGGER.warn("Unable to inspect process {}.", pid, exception);
      return Optional.empty();
    }
  }

  private static List<ProcessHandle> deepestFirst(List<ProcessHandle> descendants) {
    if (descendants == null || descendants.isEmpty()) {
      return List.of();
    }
    Map<Long, Long> parentIds = new HashMap<>();
    for (ProcessHandle descendant : descendants) {
      try {
        descendant.parent().ifPresent(parent -> parentIds.put(descendant.pid(), parent.pid()));
      } catch (RuntimeException exception) {
        LOGGER.debug("Unable to read the parent of process {}.", descendant.pid(), exception);
      }
    }
    List<ProcessHandle> ordered = new ArrayList<>(descendants);
    ordered.sort(Comparator.comparingInt(handle -> -descendantDepth(handle, parentIds)));
    return ordered;
  }

  private static int descendantDepth(ProcessHandle process, Map<Long, Long> parentIds) {
    int depth = 0;
    long processId = process.pid();
    Set<Long> visited = new HashSet<>();
    while (parentIds.containsKey(processId)) {
      long parentId = parentIds.get(processId);
      if (!visited.add(parentId)) {
        break;
      }
      depth++;
      processId = parentId;
    }
    return depth;
  }

  private static void terminate(ProcessHandle handle, Set<Long> signalledPids) {
    if (handle == null || !isSafeToTerminate(handle)) {
      return;
    }
    if (!signalledPids.add(handle.pid())) {
      return;
    }
    try {
      if (handle.isAlive()) {
        handle.destroyForcibly();
      }
    } catch (RuntimeException exception) {
      LOGGER.warn("Unable to force process {} to exit.", handle.pid(), exception);
    }
  }

  /**
   * Refuses to signal PID 1, the current JVM, or any ancestor of the current JVM. Signalling an
   * ancestor (for example a desktop session or the shell that started the test run) can take down
   * the entire host session.
   */
  static boolean isSafeToTerminate(ProcessHandle handle) {
    long pid = handle.pid();
    if (pid <= 1) {
      LOGGER.debug("Refusing to terminate protected process pid={}.", pid);
      return false;
    }

    ProcessHandle current = ProcessHandle.current();
    if (pid == current.pid()) {
      LOGGER.debug("Refusing to terminate the current JVM pid={}.", pid);
      return false;
    }

    Set<Long> ancestors = currentAncestors();
    if (ancestors.contains(pid)) {
      LOGGER.debug("Refusing to terminate an ancestor of the current JVM pid={}.", pid);
      return false;
    }
    return true;
  }

  private static Set<Long> currentAncestors() {
    Set<Long> ancestors = new HashSet<>();
    Optional<ProcessHandle> parent = ProcessHandle.current().parent();
    int depth = 0;
    while (parent.isPresent() && depth++ < MAX_ANCESTOR_DEPTH) {
      ProcessHandle ancestor = parent.get();
      if (!ancestors.add(ancestor.pid())) {
        break;
      }
      parent = ancestor.parent();
    }
    return ancestors;
  }

  private static void awaitCapturedDescendants(List<ProcessHandle> descendants) {
    if (descendants.isEmpty()) {
      return;
    }
    long deadline =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DESCENDANT_EXIT_TIMEOUT_MILLIS);
    while (descendants.stream().anyMatch(ProcessHandle::isAlive)) {
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0) {
        LOGGER.debug("Timed out waiting for terminal child processes to exit.");
        return;
      }
      try {
        TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(20)));
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static String describeProcess(ProcessHandle process) {
    ProcessHandle.Info info = process.info();
    return "pid="
        + process.pid()
        + ", alive="
        + process.isAlive()
        + ", parentPid="
        + process.parent().map(ProcessHandle::pid).orElse(-1L)
        + ", command="
        + info.command().orElse("unknown")
        + ", arguments="
        + String.join(" ", info.arguments().orElse(new String[0]));
  }

  private static void terminateRoot(Process process) {
    if (process == null) {
      return;
    }
    try {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    } catch (RuntimeException exception) {
      LOGGER.warn("Unable to force the terminal process {} to exit.", process.pid(), exception);
    }
  }

  private static boolean isMacArm64() {
    String operatingSystem = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
    String architecture = System.getProperty("os.arch").toLowerCase(java.util.Locale.ROOT);
    return operatingSystem.contains("mac")
        && (architecture.equals("aarch64") || architecture.equals("arm64"));
  }
}
