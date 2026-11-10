package io.simpleide.ui.terminal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProcessTreeTerminator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTreeTerminator.class);
    private static final int MAX_TREE_SWEEPS = 4;

    private ProcessTreeTerminator() {
    }

    static void terminate(Process process) {
        boolean windows = isWindows();
        if (windows) {
            terminateWindowsTree(process);
        } else {
            terminatePosixProcessGroup(process);
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
        terminate(root, signalledPids);
        terminateRoot(process);
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
        Set<Long> signalledPids = new HashSet<>();
        for (ProcessHandle descendant : descendants) {
            terminate(descendant, signalledPids);
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
}
