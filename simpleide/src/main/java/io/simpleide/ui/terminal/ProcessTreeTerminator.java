package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.config.LangPlatform;

public final class ProcessTreeTerminator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTreeTerminator.class);
    private static final int MAX_TREE_SWEEPS = 4;
    private static final String MACOS_PKILL = "/usr/bin/pkill";
    private static final long MACOS_PKILL_TIMEOUT_SECONDS = 5;
    private static final long MACOS_CHILD_EXIT_TIMEOUT_SECONDS = 5;

    private ProcessTreeTerminator() {
    }

    static void terminate(Process process) {
        terminate(process, isMacArm64());
    }

    static void terminate(Process process, LangPlatform platform) {
        terminate(process, platform == LangPlatform.MACOSAARCH64);
    }

    private static void terminate(Process process, boolean macArm64) {
        if (macArm64) {
            terminateMacArm64(process);
            return;
        }

        boolean windows = isWindows();
        if (windows) {
            terminateWindowsTree(process);
        }

        ProcessHandle root;
        try {
            Optional<ProcessHandle> processHandle = ProcessHandle.of(process.pid());
            if (processHandle.isEmpty()) {
                terminateRoot(process);
                return;
            }
            root = processHandle.get();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to inspect the BusyBox process tree.", exception);
            terminateRoot(process);
            return;
        }

        Set<Long> signalledPids = new HashSet<>();
        for (int sweep = 0; sweep < MAX_TREE_SWEEPS && root.isAlive(); sweep++) {
            List<ProcessHandle> descendants;
            try (var handles = root.descendants()) {
                descendants = handles.toList();
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to inspect BusyBox child processes.", exception);
                break;
            }

            for (ProcessHandle descendant : descendants) {
                terminate(descendant, signalledPids);
            }
            if (descendants.isEmpty()) {
                break;
            }
        }

        if (!windows && root.isAlive()) {
            terminatePosixProcessGroup(process);
        }
        terminate(root, signalledPids);
        terminateRoot(process);
    }

    static void terminateMacArm64(Process process) {
        ProcessHandle root;
        try {
            Optional<ProcessHandle> processHandle = ProcessHandle.of(process.pid());
            if (processHandle.isEmpty()) {
                terminateRoot(process);
                if (process.isAlive()) {
                    terminateMacArm64Pid(process.pid());
                }
                return;
            }
            root = processHandle.get();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to inspect the macOS ARM64 BusyBox process tree.", exception);
            terminateRoot(process);
            if (process.isAlive()) {
                terminateMacArm64Pid(process.pid());
            }
            return;
        }

        Map<Long, ProcessHandle> capturedDescendants = new HashMap<>();
        for (int sweep = 0; sweep < MAX_TREE_SWEEPS && root.isAlive(); sweep++) {
            List<ProcessHandle> descendants = captureDescendants(root).stream()
                .filter(descendant -> !capturedDescendants.containsKey(descendant.pid()))
                .toList();
            if (descendants.isEmpty()) {
                LOGGER.debug(
                    "No new macOS ARM64 descendants found during shutdown sweep {}: rootPid={}, "
                        + "previouslyCapturedPids={}.",
                    sweep + 1,
                    root.pid(),
                    capturedDescendants.keySet()
                );
                break;
            }
            descendants.forEach(descendant -> capturedDescendants.putIfAbsent(descendant.pid(), descendant));
            LOGGER.info(
                "Terminating macOS ARM64 process-tree sweep {}: rootPid={}, descendantPids={}.",
                sweep + 1,
                root.pid(),
                descendants.stream().map(ProcessHandle::pid).toList()
            );
            terminateMacArm64Descendants(process, descendants);
        }
        terminate(root, new HashSet<>());
        terminateRoot(process);
        if (root.isAlive()) {
            terminateMacArm64Pid(root.pid());
        }
        List<ProcessHandle> orderedDescendants = new ArrayList<>(capturedDescendants.values());
        awaitCapturedDescendants(orderedDescendants);
        for (ProcessHandle descendant : orderedDescendants) {
            if (descendant.isAlive()) {
                terminateMacArm64Pid(descendant.pid());
            }
        }
        awaitCapturedDescendants(orderedDescendants);
        LOGGER.info(
            "Completed macOS ARM64 process-tree shutdown: rootPid={}, rootAlive={}, descendantStates={}.",
            process.pid(),
            process.isAlive(),
            orderedDescendants.stream().map(ProcessTreeTerminator::describeProcess).toList()
        );
    }

    private static List<ProcessHandle> captureDescendants(ProcessHandle root) {
        try (var handles = root.descendants()) {
            return handles.toList();
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to capture macOS ARM64 BusyBox child processes.", exception);
            return List.of();
        }
    }

    static List<ProcessHandle> captureDescendants(Process process) {
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

    public static void terminateCapturedDescendants(List<ProcessHandle> descendants, LangPlatform platform) {
        terminateCapturedDescendants(descendants, platform == LangPlatform.MACOSAARCH64);
    }

    private static void terminateCapturedDescendants(List<ProcessHandle> descendants, boolean macArm64) {
        if (macArm64) {
            terminateMacArm64Descendants(descendants);
            return;
        }
        Set<Long> signalledPids = new HashSet<>();
        for (ProcessHandle descendant : descendants) {
            terminate(descendant, signalledPids);
        }
    }

    static void terminateMacArm64Descendants(List<ProcessHandle> descendants) {
        terminateMacArm64Descendants(null, descendants);
    }

    static void terminateMacArm64Descendants(Process root, List<ProcessHandle> descendants) {
        Map<Long, Long> parentIds = new HashMap<>();
        for (ProcessHandle descendant : descendants) {
            descendant.parent().ifPresent(parent -> parentIds.put(descendant.pid(), parent.pid()));
        }
        List<ProcessHandle> ordered = new ArrayList<>(descendants);
        ordered.sort(Comparator.comparingInt(handle -> -descendantDepth(handle, parentIds)));
        Set<Long> signalledPids = new HashSet<>();
        Set<Long> signalledParents = new HashSet<>();
        for (ProcessHandle descendant : ordered) {
            long parentPid = parentIds.getOrDefault(descendant.pid(), -1L);
            if (parentPid > 0 && signalledParents.add(parentPid)) {
                terminateMacArm64Children(parentPid);
            }
            terminate(descendant, signalledPids);
        }
        if (root != null) {
            if (signalledParents.add(root.pid())) {
                terminateMacArm64Children(root.pid());
            }
        } else {
            awaitCapturedDescendants(ordered);
            for (ProcessHandle descendant : ordered) {
                if (descendant.isAlive()) {
                    terminateMacArm64Pid(descendant.pid());
                }
            }
            awaitCapturedDescendants(ordered);
        }
    }

    private static void terminateMacArm64Children(long parentPid) {
        if (!isLiveProcess(parentPid)) {
            return;
        }

        List<ProcessHandle> directChildren;
        try (var descendants = ProcessHandle.of(parentPid).orElseThrow().descendants()) {
            directChildren = descendants
                .filter(child -> child.parent().map(ProcessHandle::pid).orElse(-1L) == parentPid)
                .toList();
            if (directChildren.isEmpty()) {
                LOGGER.debug("No direct children remain under macOS ARM64 process {} before pkill.", parentPid);
                return;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to verify children of macOS ARM64 process {} before pkill.", parentPid, exception);
            return;
        }

        Process pkill;
        long startedAt = System.nanoTime();
        try {
            pkill = new ProcessBuilder(
                MACOS_PKILL,
                "-KILL",
                "-P",
                Long.toString(parentPid)
            ).redirectErrorStream(true).start();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to start pkill for macOS ARM64 parent process {}.", parentPid, exception);
            return;
        }

        try {
            LOGGER.info(
                "Running scoped macOS ARM64 pkill: parentPid={}, directChildPids={}, timeoutSeconds={}.",
                parentPid,
                directChildren.stream().map(ProcessHandle::pid).toList(),
                MACOS_PKILL_TIMEOUT_SECONDS
            );
            if (!pkill.waitFor(MACOS_PKILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                pkill.destroyForcibly();
                LOGGER.warn(
                    "Timed out running scoped pkill: parentPid={}, directChildPids={}, elapsedMillis={}.",
                    parentPid,
                    directChildren.stream().map(ProcessHandle::pid).toList(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                );
            } else {
                String output = new String(pkill.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (pkill.exitValue() == 0) {
                    LOGGER.info(
                        "Scoped pkill completed: parentPid={}, exitCode=0, elapsedMillis={}, output={}.",
                        parentPid,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                        output
                    );
                } else if (pkill.exitValue() == 1) {
                    LOGGER.debug(
                        "Scoped pkill found no remaining direct children: parentPid={}, elapsedMillis={}, output={}.",
                        parentPid,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                        output
                    );
                } else {
                    LOGGER.warn(
                        "Scoped pkill failed: parentPid={}, exitCode={}, elapsedMillis={}, output={}.",
                        parentPid,
                        pkill.exitValue(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                        output
                    );
                }
            }
        } catch (InterruptedException exception) {
            pkill.destroyForcibly();
            Thread.currentThread().interrupt();
            LOGGER.warn(
                "Interrupted running scoped pkill: parentPid={}, directChildPids={}.",
                parentPid,
                directChildren.stream().map(ProcessHandle::pid).toList(),
                exception
            );
        } catch (IOException exception) {
            LOGGER.warn("Unable to read scoped pkill output for parent process {}.", parentPid, exception);
        }
    }

    private static boolean isLiveProcess(long pid) {
        try {
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to verify macOS ARM64 process {} before termination.", pid, exception);
            return false;
        }
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

    private static void awaitCapturedDescendants(List<ProcessHandle> descendants) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MACOS_CHILD_EXIT_TIMEOUT_SECONDS);
        while (descendants.stream().anyMatch(ProcessHandle::isAlive)) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                LOGGER.debug("Timed out waiting for macOS ARM64 terminal children to exit.");
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
        return "pid=" + process.pid()
            + ", alive=" + process.isAlive()
            + ", parentPid=" + process.parent().map(ProcessHandle::pid).orElse(-1L)
            + ", command=" + info.command().orElse("unknown")
            + ", arguments=" + String.join(" ", info.arguments().orElse(new String[0]));
    }

    private static void terminateMacArm64Pid(long pid) {
        Process kill;
        long startedAt = System.nanoTime();
        try {
            LOGGER.info("Sending scoped SIGKILL to macOS ARM64 process pid={}.", pid);
            kill = new ProcessBuilder("/bin/kill", "-KILL", Long.toString(pid))
                .redirectErrorStream(true)
                .start();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to signal macOS ARM64 terminal child process {}.", pid, exception);
            return;
        }
        try {
            if (!kill.waitFor(1, TimeUnit.SECONDS)) {
                kill.destroyForcibly();
                LOGGER.warn("Timed out signaling macOS ARM64 terminal child process {}.", pid);
            } else if (kill.exitValue() != 0) {
                String output = new String(kill.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!isLiveProcess(pid)) {
                    LOGGER.debug(
                        "Process exited while signaling macOS ARM64 PID {} (exitCode={}, elapsedMillis={}, output={}).",
                        pid,
                        kill.exitValue(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                        output
                    );
                } else {
                    LOGGER.warn(
                        "Unable to signal macOS ARM64 terminal child process {} (exit code {}): {}",
                        pid,
                        kill.exitValue(),
                        output
                    );
                }
            } else {
                LOGGER.info(
                    "Scoped SIGKILL completed for macOS ARM64 PID {} in {}ms.",
                    pid,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                );
            }
        } catch (InterruptedException exception) {
            kill.destroyForcibly();
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted signaling macOS ARM64 terminal child process {}.", pid, exception);
        } catch (IOException exception) {
            LOGGER.warn("Unable to read macOS ARM64 terminal child termination output.", exception);
        }
    }

    private static void terminatePosixProcessGroup(Process process) {
        Process groupQuery;
        try {
            groupQuery = new ProcessBuilder(
                "/bin/ps",
                "-o",
                "pgid=",
                "-p",
                Long.toString(process.pid())
            ).redirectErrorStream(true).start();
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to query the BusyBox process group; falling back to process handles.", exception);
            return;
        }

        Process groupKill = null;
        try {
            if (!groupQuery.waitFor(2, TimeUnit.SECONDS)) {
                groupQuery.destroyForcibly();
                LOGGER.warn("Timed out querying the BusyBox process group.");
                return;
            }
            String processGroupOutput = new String(groupQuery.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (groupQuery.exitValue() != 0) {
                LOGGER.warn(
                    "Unable to query the BusyBox process group (exit code {}): {}; "
                        + "falling back to process handles.",
                    groupQuery.exitValue(),
                    processGroupOutput
                );
                return;
            }

            if (processGroupOutput.isEmpty() || Long.parseLong(processGroupOutput) != process.pid()) {
                LOGGER.debug(
                    "BusyBox process {} is not the leader of its process group; using process handles.",
                    process.pid()
                );
                return;
            }

            groupKill = new ProcessBuilder(
                "/bin/kill",
                "-KILL",
                "-" + process.pid()
            ).redirectErrorStream(true).start();
            if (!groupKill.waitFor(2, TimeUnit.SECONDS)) {
                groupKill.destroyForcibly();
                LOGGER.warn("Timed out terminating the BusyBox process group.");
            } else {
                String killOutput = new String(groupKill.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (groupKill.exitValue() != 0) {
                    LOGGER.warn(
                        "Unable to terminate the BusyBox process group (exit code {}): {}; "
                            + "falling back to process handles.",
                        groupKill.exitValue(),
                        killOutput
                    );
                }
            }
        } catch (InterruptedException exception) {
            groupQuery.destroyForcibly();
            if (groupKill != null) {
                groupKill.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while terminating the BusyBox process group.", exception);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to terminate the BusyBox process group; falling back to process handles.", exception);
        }
    }

    private static void terminateWindowsTree(Process process) {
        Process taskkill;
        try {
            taskkill = new ProcessBuilder(
                "taskkill",
                "/PID",
                Long.toString(process.pid()),
                "/T",
                "/F"
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Unable to terminate the BusyBox process tree with taskkill.", exception);
            return;
        }

        try {
            if (!taskkill.waitFor(5, TimeUnit.SECONDS)) {
                taskkill.destroyForcibly();
                LOGGER.error("Timed out while terminating the BusyBox process tree with taskkill.");
            } else if (taskkill.exitValue() != 0) {
                LOGGER.error(
                    "taskkill failed to terminate the BusyBox process tree with exit code {}.",
                    taskkill.exitValue()
                );
            }
        } catch (InterruptedException exception) {
            taskkill.destroyForcibly();
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while terminating the BusyBox process tree with taskkill.", exception);
        }
    }

    private static void terminate(ProcessHandle handle, Set<Long> signalledPids) {
        if (!signalledPids.add(handle.pid())) {
            return;
        }
        try {
            handle.destroyForcibly();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to force process {} to exit.", handle.pid(), exception);
        }
    }

    private static void terminateRoot(Process process) {
        try {
            process.destroyForcibly();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to force the BusyBox process to exit.", exception);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMacArm64() {
        String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return operatingSystem.contains("mac") && (architecture.equals("aarch64") || architecture.equals("arm64"));
    }
}
