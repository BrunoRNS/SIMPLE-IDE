package io.simpleide.ui.terminal;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProcessTreeTerminator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTreeTerminator.class);
    private static final int MAX_TREE_SWEEPS = 4;

    private ProcessTreeTerminator() {
    }

    static void terminate(Process process) {
        ProcessHandle root;
        try {
            Optional<ProcessHandle> processHandle = ProcessHandle.of(process.pid());
            if (processHandle.isEmpty()) {
                process.destroyForcibly();
                return;
            }
            root = processHandle.get();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to inspect the BusyBox process tree.", exception);
            process.destroyForcibly();
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

            boolean discoveredChild = false;
            for (ProcessHandle descendant : descendants) {
                if (signalledPids.add(descendant.pid())) {
                    discoveredChild = true;
                    terminate(descendant);
                }
            }
            if (!discoveredChild) {
                break;
            }
        }
        terminate(root);
    }

    private static void terminate(ProcessHandle handle) {
        try {
            handle.destroyForcibly();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to force process {} to exit.", handle.pid(), exception);
        }
    }
}
