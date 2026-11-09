package io.simpleide.ui.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

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

    private static ProcessHandle awaitChild(Process process) throws InterruptedException {
        await("The test process should create its child process")
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> process.toHandle().descendants().findFirst().isPresent());
        return process.toHandle().descendants().findFirst().orElseThrow();
    }
}
