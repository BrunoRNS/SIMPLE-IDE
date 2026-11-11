package io.simpleide.ui.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import org.junit.jupiter.api.Test;

class ProcessTreeTerminatorTest {

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

            assertTrue(root.waitFor(5, TimeUnit.SECONDS));
            child.onExit().get(5, TimeUnit.SECONDS);
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

            assertTrue(root.waitFor(5, TimeUnit.SECONDS));
            child.onExit().get(5, TimeUnit.SECONDS);
            assertFalse(child.isAlive());
        } finally {
            if (root.isAlive()) {
                ProcessTreeTerminator.terminate(root);
            }
        }
    }

    @Test
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

            assertTrue(root.waitFor(5, TimeUnit.SECONDS));
            child.onExit().get(5, TimeUnit.SECONDS);
            assertFalse(child.isAlive());
        } finally {
            if (root.isAlive()) {
                ProcessTreeTerminator.terminate(root);
            }
        }
    }

    private static ProcessHandle awaitChild(Process process) throws InterruptedException {
        await("The test process should create its child process")
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> process.toHandle().descendants().findFirst().isPresent());
        return process.toHandle().descendants().findFirst().orElseThrow();
    }

    private static ProcessHandle awaitSeparateProcessGroupChild(Process process) throws Exception {
        long rootGroup = processGroupId(process.pid());
        AtomicReference<ProcessHandle> child = new AtomicReference<>();
        await("The test process should create a child in a separate process group")
            .atMost(5, TimeUnit.SECONDS)
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
        Process query = new ProcessBuilder("/bin/ps", "-o", "pgid=", "-p", Long.toString(pid)).start();
        assertTrue(query.waitFor(2, TimeUnit.SECONDS), "Process group query timed out.");
        assertEquals(0, query.exitValue());
        return Long.parseLong(new String(query.getInputStream().readAllBytes()).trim());
    }
}
