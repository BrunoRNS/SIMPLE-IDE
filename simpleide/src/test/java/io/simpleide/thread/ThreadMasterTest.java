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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.edt.EDTNode;
import io.simpleide.thread.nodes.runnable.RunnableNode;

class ThreadMasterTest {

    @Test
    public void exposesOneThreadMasterInstance() {
        assertSame(ThreadMaster.getInstance(), ThreadMasterHolder.INSTANCE);
    }

    @Test
    public void registersNodesInParentLevelOrderAndRemovesSubtrees() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        try {
            RunnableNode parent = new RunnableNode(ThreadMaster.ROOT, () -> {});
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
    public void initializesRegisteredWorkOnceAndCapsConcurrencyForTwentyTasks() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        int taskCount = 20;
        CountDownLatch firstWorkersStarted = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CountDownLatch allTasksFinished = new CountDownLatch(taskCount);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        try {
            for (int i = 0; i < taskCount; i++) {
                master.addNode(new RunnableNode(ThreadMaster.ROOT, () -> {
                    starts.incrementAndGet();
                    assertFalse(SwingUtilities.isEventDispatchThread());
                    int current = active.incrementAndGet();
                    maximumActive.accumulateAndGet(current, Math::max);
                    firstWorkersStarted.countDown();
                    try {
                        assertTrue(releaseWorkers.await(5, TimeUnit.SECONDS));
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

            assertTrue(firstWorkersStarted.await(5, TimeUnit.SECONDS));
            assertEquals(2, maximumActive.get());
            releaseWorkers.countDown();
            assertTrue(allTasksFinished.await(5, TimeUnit.SECONDS));
            awaitValue(closes, taskCount);
            assertEquals(taskCount, starts.get());
            assertEquals(taskCount, closes.get());
            assertTrue(maximumActive.get() <= 2);
        } finally {
            releaseWorkers.countDown();
            master.shutdown();
        }
    }

    @Test
    public void shutdownInterruptsRunningWorkAndRunsCloseExactlyOnce() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger closeCount = new AtomicInteger();
        RunnableNode node = new RunnableNode(ThreadMaster.ROOT, () -> {
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
        assertTrue(started.await(5, TimeUnit.SECONDS));

        master.shutdown();

        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertEquals(1, closeCount.get());
        IllegalStateException exception = assertThrows(IllegalStateException.class, master::initialize);
        assertSame(IllegalStateException.class, exception.getClass());
    }

    @Test
    public void dispatchesEdtNodesOnSwingEventDispatchThread() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch finished = new CountDownLatch(1);
        try {
            master.register(new EDTNode(() -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                finished.countDown();
            }));
            master.initialize();

            assertTrue(finished.await(5, TimeUnit.SECONDS));
        } finally {
            master.shutdown();
        }
    }

    @Test
    public void runnableNodeDoesNotRunOnOrBlockTheEdtNode() throws Exception {
        TestThreadMaster master = new TestThreadMaster();
        CountDownLatch runnableStarted = new CountDownLatch(1);
        CountDownLatch releaseRunnable = new CountDownLatch(1);
        CountDownLatch edtFinished = new CountDownLatch(1);
        AtomicBoolean runnableUsedEdt = new AtomicBoolean(true);
        AtomicBoolean edtUsedEdt = new AtomicBoolean();
        try {
            master.addNode(new RunnableNode(ThreadMaster.ROOT, () -> {
                runnableUsedEdt.set(SwingUtilities.isEventDispatchThread());
                runnableStarted.countDown();
                try {
                    releaseRunnable.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }));
            master.addNode(new EDTNode(() -> {
                edtUsedEdt.set(SwingUtilities.isEventDispatchThread());
                edtFinished.countDown();
            }));
            master.initialize();

            assertTrue(runnableStarted.await(5, TimeUnit.SECONDS));
            assertTrue(edtFinished.await(5, TimeUnit.SECONDS));
            assertFalse(runnableUsedEdt.get());
            assertTrue(edtUsedEdt.get());
        } finally {
            releaseRunnable.countDown();
            master.shutdown();
        }
    }

    @Test
    public void executeOnEdtRegistersRunsClosesAndRemovesTransientNode() throws Exception {
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

            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertTrue(closeRanOnEdt.get());
            assertEquals(1, master.showTree().size());

            await().atMost(5, TimeUnit.SECONDS).until(() -> master.showTree().size() == 1);

        } finally {
            master.shutdown();
        }
    }

    @Test
    public void rejectsInvalidLevelsAndRemovalOfRoot() {
        TestThreadMaster master = new TestThreadMaster();
        try {
            RunnableNode parent = new RunnableNode(ThreadMaster.ROOT, () -> {});
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
            .hasMessageContaining("");

            assertThatThrownBy(() -> master.removeNodeCascade(ThreadMaster.ROOT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("");

        } finally {
            master.shutdown();
        }
    }


    private static void awaitValue(AtomicInteger value, int expected) {
        await("Expected AtomicInteger value to become " + expected)
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(5, TimeUnit.MILLISECONDS)
            .until(() -> value.get() == expected);
    }


    private static final class TestThreadMaster extends AbstractThreadMaster {
        private TestThreadMaster() {
            super(ThreadMaster.ROOT);
        }
    }
}
