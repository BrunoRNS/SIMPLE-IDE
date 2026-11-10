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
        if (!windows) {
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
                terminatePosixProcessGroup(process);
                terminateRoot(process);
                return;
            }
            root = processHandle.get();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to inspect the macOS ARM64 BusyBox process tree.", exception);
            terminatePosixProcessGroup(process);
            terminateRoot(process);
            return;
        }

        List<ProcessHandle> capturedDescendants = captureDescendants(root);
        terminateMacArm64Descendants(capturedDescendants);
        if (root.isAlive()) {
            terminatePosixProcessGroup(process);
        }
        terminateMacArm64Descendants(capturedDescendants);

        for (int sweep = 0; sweep < MAX_TREE_SWEEPS && root.isAlive(); sweep++) {
            List<ProcessHandle> descendants = captureDescendants(root);
            if (descendants.isEmpty()) {
                break;
            }
            terminateMacArm64Descendants(descendants);
        }
        terminate(root, new HashSet<>());
        terminateRoot(process);
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

    static void terminateCapturedDescendants(List<ProcessHandle> descendants) {
        terminateCapturedDescendants(descendants, isMacArm64());
    }

    static void terminateCapturedDescendants(List<ProcessHandle> descendants, LangPlatform platform) {
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
        Map<Long, Long> parentIds = new HashMap<>();
        for (ProcessHandle descendant : descendants) {
            descendant.parent().ifPresent(parent -> parentIds.put(descendant.pid(), parent.pid()));
        }
        List<ProcessHandle> ordered = new ArrayList<>(descendants);
        ordered.sort(Comparator.comparingInt(handle -> -descendantDepth(handle, parentIds)));
        Set<Long> signalledPids = new HashSet<>();
        for (ProcessHandle descendant : ordered) {
            terminate(descendant, signalledPids);
        }
        awaitCapturedDescendants(ordered);
        for (ProcessHandle descendant : ordered) {
            if (descendant.isAlive()) {
                terminateMacArm64Pid(descendant.pid());
            }
        }
        awaitCapturedDescendants(ordered);
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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void terminateMacArm64Pid(long pid) {
        Process kill;
        try {
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
                LOGGER.warn(
                    "Unable to signal macOS ARM64 terminal child process {} (exit code {}): {}",
                    pid,
                    kill.exitValue(),
                    output
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
