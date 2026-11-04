package io.simpleide.thread;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.edt.EDTNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

class ThreadMasterTest {

    private static final int TEST_TIMEOUT_SECONDS = 2;
    private static final int STABILITY_WINDOW_MILLISECONDS = 200;

    @Test
    void exposesOneThreadMasterInstance() {
        assertSame(ThreadMaster.getInstance(), ThreadMasterHolder.INSTANCE);
    }

    @Test
    void retainsTheRootProvidedToItsConstructor() {
        NodePattern root = new NodePattern() {
            @Override
            public Runnable onStart() {
                return () -> {};
            }

            @Override
            public Runnable onClose() {
                return null;
            }

            @Override
            public Integer level() {
                return 0;
            }

            @Override
            public boolean isEDT() {
                return false;
            }
        };
        ThreadMaster master = new ThreadMaster(root);
        try {
            assertSame(root, master.getROOT());
            assertSame(root, master.getRoot().getNode());
        } finally {
            master.shutdown();
        }
    }

    @Test
    void constructingNodeDoesNotInvokeParentActions() {
        AtomicInteger parentActions = new AtomicInteger();
        NodePattern root = new NodePattern() {
            @Override
            public Runnable onStart() {
                parentActions.incrementAndGet();
                return () -> {};
            }

            @Override
            public Runnable onClose() {
                parentActions.incrementAndGet();
                return () -> {};
            }

            @Override
            public Integer level() {
                return 0;
            }

            @Override
            public boolean isEDT() {
                return false;
            }
        };

        new RunnableNode(root, () -> {});

        assertEquals(0, parentActions.get());
    }

    @Test
    void registersNodesInParentLevelOrderAndRemovesSubtrees() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        try {
            RunnableNode parent = new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {});
            RunnableNode child = new RunnableNode(parent, () -> {});

            master.addNode(parent);
            master.addNode(child);

            assertEquals(
                new LinkedList<>(java.util.List.of(
                    master.getRoot().getId(),
                    parent.getId(),
                    child.getId()
                )),
                master.bfs(master.getRoot(), new LinkedList<>())
            );
            assertEquals(3, master.showTree().size());

            master.removeNodeCascade(parent);

            assertEquals(1, master.showTree().size());
            assertFalse(master.showTree().containsKey(parent.getId()));
            assertFalse(master.showTree().containsKey(child.getId()));
        } finally {
            master.shutdown();
        }
    }

    @Test
    void initializesRegisteredWorkOnceAndCapsConcurrencyForTwentyTasks() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        int workerCount = ThreadTaskScheduler.WORKER_COUNT;
        int taskCount = 20;
        CountDownLatch firstWorkersStarted = new CountDownLatch(workerCount);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CountDownLatch allTasksFinished = new CountDownLatch(taskCount);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        try {
            for (int i = 0; i < taskCount; i++) {
                master.addNode(new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {
                    starts.incrementAndGet();
                    assertFalse(SwingUtilities.isEventDispatchThread());
                    int current = active.incrementAndGet();
                    maximumActive.accumulateAndGet(current, Math::max);
                    firstWorkersStarted.countDown();
                    try {
                        assertTrue(releaseWorkers.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        active.decrementAndGet();
                        allTasksFinished.countDown();
                    }
                }, closes::incrementAndGet));
            }

            master.initialize();
            master.initialize();

            assertTrue(firstWorkersStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(workerCount, maximumActive.get());
            releaseWorkers.countDown();
            assertTrue(allTasksFinished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            awaitValue(closes, taskCount);
            assertEquals(taskCount, starts.get());
            assertEquals(taskCount, closes.get());
            assertTrue(maximumActive.get() <= workerCount);
            await()
                .during(STABILITY_WINDOW_MILLISECONDS, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(taskCount, starts.get()));
        } finally {
            releaseWorkers.countDown();
            master.shutdown();
        }
    }

    @Test
    void registeringCompletedNodeDoesNotScheduleItAgain() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger startCount = new AtomicInteger();
        RunnableNode node = new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {
            startCount.incrementAndGet();
            started.countDown();
        }, closed::countDown);

        try {
            master.register(node);
            master.initialize();
            assertTrue(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            master.register(node);

            await()
                .during(STABILITY_WINDOW_MILLISECONDS, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, startCount.get()));
        } finally {
            master.shutdown();
        }
    }

    @Test
    void reportsWhetherNodeIsRunningOrRegistered() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        RunnableNode node = new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {
            started.countDown();
            try {
                assertTrue(release.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, closed::countDown);

        try {
            assertNull(master.isNodeRunning(node));

            master.register(node);
            assertEquals(Boolean.FALSE, master.isNodeRunning(node));

            master.initialize();
            assertTrue(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE, master.isNodeRunning(node));

            release.countDown();
            assertTrue(closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            await().atMost(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> Boolean.FALSE.equals(master.isNodeRunning(node)));

            master.removeNodeCascade(node);
            assertNull(master.isNodeRunning(node));
        } finally {
            release.countDown();
            master.shutdown();
        }
    }

    @Test
    void shutdownInterruptsRunningWorkAndRunsCloseExactlyOnce() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger closeCount = new AtomicInteger();
        RunnableNode node = new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, () -> {
            closeCount.incrementAndGet();
            closed.countDown();
        });

        master.register(node);
        master.initialize();
        assertTrue(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        master.shutdown();

        assertTrue(closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, closeCount.get());
        assertThatThrownBy(master::initialize).isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void dispatchesEdtNodesOnSwingEventDispatchThread() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch finished = new CountDownLatch(1);
        try {
            master.register(new EDTNode(() -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                finished.countDown();
            }));
            master.initialize();

            assertTrue(finished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            master.shutdown();
        }
    }

    @Test
    void runnableNodeDoesNotRunOnOrBlockTheEdtNode() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch runnableStarted = new CountDownLatch(1);
        CountDownLatch releaseRunnable = new CountDownLatch(1);
        CountDownLatch edtFinished = new CountDownLatch(1);
        AtomicBoolean runnableUsedEdt = new AtomicBoolean(true);
        AtomicBoolean edtUsedEdt = new AtomicBoolean();
        try {
            master.addNode(new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {
                runnableUsedEdt.set(SwingUtilities.isEventDispatchThread());
                runnableStarted.countDown();
                try {
                    releaseRunnable.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));
            master.addNode(new EDTNode(() -> {
                edtUsedEdt.set(SwingUtilities.isEventDispatchThread());
                edtFinished.countDown();
            }));
            master.initialize();

            assertTrue(runnableStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(edtFinished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertFalse(runnableUsedEdt.get());
            assertTrue(edtUsedEdt.get());
        } finally {
            releaseRunnable.countDown();
            master.shutdown();
        }
    }

    @Test
    void executeOnEdtRegistersRunsClosesAndRemovesTransientNode() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean closeRanOnEdt = new AtomicBoolean();
        try {
            master.executeOnEDT(
                () -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    assertEquals(2, master.showTree().size());
                    started.countDown();
                },
                () -> {
                    closeRanOnEdt.set(SwingUtilities.isEventDispatchThread());
                    closed.countDown();
                }
            );

            assertTrue(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(closeRanOnEdt.get());
            assertEquals(1, master.showTree().size());

            await().atMost(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .until(() -> master.showTree().size() == 1);

        } finally {
            master.shutdown();
        }
    }

    @Test
    void rejectsInvalidLevelsAndRemovalOfRoot() {
        TestThreadMaster master = new TestThreadMaster();
        try {
            RunnableNode parent = new RunnableNode(ThreadMaster.getInstance().getROOT(), () -> {});
            master.addNode(parent);
        
            assertThatThrownBy(() -> master.addNode(new NodePattern() {
                @Override
                public Runnable onStart() { return null; }

                @Override
                public Runnable onClose() { return null; }

                @Override
                public Integer level() { return 0; }

                @Override
                public boolean isEDT() { return false; }
            }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive level");

            assertThatThrownBy(() -> master.removeNodeCascade(ThreadMaster.getInstance().getROOT()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be removed");

        } finally {
            master.shutdown();
        }
    }

    @Test
    void rejectsBuiltInNodesWithInconsistentExecutionModes() {
        TestThreadMaster master = new TestThreadMaster();
        try {
            RunnableNode runnableOnEdt = new RunnableNode(
                ThreadMaster.getInstance().getROOT(),
                () -> {}
            ) {
                @Override
                public boolean isEDT() {
                    return true;
                }
            };
            EDTNode edtOffEdt = new EDTNode(() -> {}) {
                @Override
                public boolean isEDT() {
                    return false;
                }
            };

            assertThatThrownBy(() -> master.register(runnableOnEdt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RunnableNode");
            assertThatThrownBy(() -> master.register(edtOffEdt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EDTNode");
        } finally {
            master.shutdown();
        }
    }


    private static void awaitValue(AtomicInteger value, int expected) {
        await("Expected AtomicInteger value to become " + expected)
            .atMost(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pollInterval(5, TimeUnit.MILLISECONDS)
            .until(() -> value.get() == expected);
    }


    private static final class TestThreadMaster extends AbstractThreadMaster {
        private TestThreadMaster() {
            super(ThreadMaster.getInstance().getROOT());
        }
    }
}
