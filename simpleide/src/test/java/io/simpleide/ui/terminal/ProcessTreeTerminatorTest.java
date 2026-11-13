package io.simpleide.ui.terminal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.awaitility.Awaitility.await;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessTreeTerminatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTreeTerminatorTest.class);
    private static final long PROCESS_START_TIMEOUT_SECONDS = 15;
    private static final long PROCESS_EXIT_TIMEOUT_SECONDS = 60;

    @Test
    void forciblyTerminatesTheRootAndItsChildProcesses() throws Exception {
        assumeFalse(
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
            "This test requires a POSIX shell"
        );
        Process root = new ProcessBuilder("sh", "-c", "sleep 30 & wait").start();
        try {
            ProcessHandle child = awaitChild(root);
            assertTrue(root.isAlive());
            assertTrue(child.isAlive());

            ProcessTreeTerminator.terminate(root);

            assertTrue(root.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            child.onExit().get(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertFalse(child.isAlive());
        } finally {
            if (root.isAlive()) {
                ProcessTreeTerminator.terminate(root);
            }
        }
    }

    @Test
    void terminatesAProcessGroupLeaderAndItsChildren() throws Exception {
        assumeTrue(
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
            "This test requires Linux setsid"
        );
        Process root = new ProcessBuilder("setsid", "sh", "-c", "sleep 30 & wait").start();
        try {
            ProcessHandle child = awaitChild(root);
            assertTrue(root.isAlive());
            assertTrue(child.isAlive());

            ProcessTreeTerminator.terminate(root);

            assertTrue(root.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            child.onExit().get(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertFalse(child.isAlive());
        } finally {
            if (root.isAlive()) {
                ProcessTreeTerminator.terminate(root);
            }
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void terminatesDescendantsInSeparateProcessGroupsBeforeTheRoot() throws Exception {
        assumeTrue(
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux"),
            "This test requires Linux setsid"
        );
        Process root = new ProcessBuilder(
            "setsid",
            "sh",
            "-c",
            "setsid sleep 30 & wait"
        ).start();
        try {
            ProcessHandle child = awaitSeparateProcessGroupChild(root);
            assertTrue(root.isAlive());
            assertTrue(child.isAlive());

            ProcessTreeTerminator.terminateMacArm64(root);

            assertTrue(
                root.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Root process did not exit: " + describeProcess(root.toHandle())
            );
            awaitProcessExit(child);
            assertFalse(child.isAlive());
        } finally {
            if (root.isAlive()) {
                ProcessTreeTerminator.terminate(root);
            }
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void terminatesMacArm64ProcessTreeWithNestedChildren() throws Exception {
        assumeFalse(
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
            "This test requires a POSIX shell and pkill."
        );
        Process root = new ProcessBuilder(
            "sh",
            "-c",
            "sh -c 'sleep 30 & wait' & wait"
        ).start();
        try {
            List<ProcessHandle> descendants = awaitDescendants(root, 2);
            assertTrue(root.isAlive());
            assertTrue(descendants.stream().allMatch(ProcessHandle::isAlive));
            LOGGER.info(
                "Starting nested macOS ARM64 termination test: root={}, descendants={}.",
                describeProcess(root.toHandle()),
                descendants.stream().map(ProcessTreeTerminatorTest::describeProcess).toList()
            );

            long terminationStartedAt = System.nanoTime();
            LOGGER.info("Calling macOS ARM64 process-tree terminator for rootPid={}.", root.pid());
            ProcessTreeTerminator.terminateMacArm64(root);
            LOGGER.info(
                "MacOS ARM64 process-tree terminator returned after {}ms: root={}, descendants={}.",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - terminationStartedAt),
                describeProcess(root.toHandle()),
                descendants.stream().map(ProcessTreeTerminatorTest::describeProcess).toList()
            );

            assertTrue(
                root.waitFor(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Root process did not exit: " + describeProcess(root.toHandle())
            );
            descendants.forEach(ProcessTreeTerminatorTest::awaitProcessExit);
        } finally {
            if (root.isAlive()) {
                ProcessTreeTerminator.terminateMacArm64(root);
            }
        }
    }

    private static ProcessHandle awaitChild(Process process) throws InterruptedException {
        AtomicReference<ProcessHandle> child = new AtomicReference<>();
        await("The test process should create a child: " + describeProcess(process.toHandle()))
            .atMost(PROCESS_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .until(() -> {
                child.set(readDescendants(process).stream().findFirst().orElse(null));
                return child.get() != null;
            });
        return child.get();
    }

    private static List<ProcessHandle> awaitDescendants(Process process, int expectedCount) {
        AtomicReference<List<ProcessHandle>> descendants = new AtomicReference<>(List.of());
        await("The nested shell should create at least " + expectedCount + " descendants")
            .atMost(PROCESS_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .until(() -> {
                descendants.set(readDescendants(process));
                return descendants.get().size() >= expectedCount;
            });
        return descendants.get();
    }

    private static List<ProcessHandle> readDescendants(Process process) {
        try (var handles = process.toHandle().descendants()) {
            return handles.toList();
        }
    }

    private static void awaitProcessExit(ProcessHandle process) {
        try {
            await("Process should exit: " + describeProcess(process))
                .atMost(PROCESS_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> !process.isAlive());
        } catch (ConditionTimeoutException exception) {
            throw new AssertionError(
                "Process remained alive: " + describeProcess(process),
                exception
            );
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

    private static ProcessHandle awaitSeparateProcessGroupChild(Process process) throws Exception {
        long rootGroup = processGroupId(process.pid());
        AtomicReference<ProcessHandle> child = new AtomicReference<>();
        await("The test process should create a child in a separate process group")
            .atMost(PROCESS_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .until(() -> {
                child.set(findSeparateProcessGroupChild(process, rootGroup));
                return child.get() != null;
            });
        return child.get();
    }

    private static ProcessHandle findSeparateProcessGroupChild(Process process, long rootGroup) throws Exception {
        try (var descendants = process.toHandle().descendants()) {
            for (ProcessHandle descendant : descendants.toList()) {
                if (processGroupId(descendant.pid()) != rootGroup) {
                    return descendant;
                }
            }
        }
        return null;
    }

    private static long processGroupId(long pid) throws Exception {
        String stat = Files.readString(Path.of("/proc", Long.toString(pid), "stat"));
        int commandEnd = stat.lastIndexOf(')');
        assertTrue(commandEnd >= 0, "Unexpected /proc stat format: " + stat);
        String[] fields = stat.substring(commandEnd + 2).trim().split("\\s+");
        assertTrue(fields.length >= 3, "Unexpected /proc stat format: " + stat);
        return Long.parseLong(fields[2]);
    }
}
