package io.simpleide.thread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.nodes.NodePattern;

class ThreadTaskSchedulerTest {

    private static final int TEST_TIMEOUT_SECONDS = 10;

    @Test
    void schedulesEachNodeOnlyOnceAndCallsItsCloseActionOnce() throws Exception {
        ThreadTaskScheduler scheduler = new ThreadTaskScheduler();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicInteger startCount = new AtomicInteger();
        AtomicInteger closeCount = new AtomicInteger();
        NodePattern node = new TestPattern(false, () -> {
            startCount.incrementAndGet();
            started.countDown();
        }, () -> {
            closeCount.incrementAndGet();
            closed.countDown();
        });

        try {
            scheduler.schedule(node);
            scheduler.schedule(node);
            assertTrue(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            scheduler.schedule(node);
            await()
                .during(500, TimeUnit.MILLISECONDS)
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertEquals(1, startCount.get());
                    assertEquals(1, closeCount.get());
                });
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void cancelledManagedTaskSkipsStartAndRunsCloseOnce() {
        AtomicInteger startCount = new AtomicInteger();
        AtomicInteger closeCount = new AtomicInteger();
        AtomicInteger finishedCount = new AtomicInteger();
        NodePattern node = new TestPattern(false, startCount::incrementAndGet, closeCount::incrementAndGet);
        ManagedTask task = new ManagedTask(node, ignored -> finishedCount.incrementAndGet());

        task.cancel(() -> {});
        task.run();
        task.run();

        assertEquals(0, startCount.get());
        assertEquals(1, closeCount.get());
        assertEquals(1, finishedCount.get());
    }

    @Test
    void managedTaskRunsAndClosesOnlyOnce() {
        AtomicInteger startCount = new AtomicInteger();
        AtomicInteger closeCount = new AtomicInteger();
        AtomicInteger finishedCount = new AtomicInteger();
        NodePattern node = new TestPattern(false, startCount::incrementAndGet, closeCount::incrementAndGet);
        ManagedTask task = new ManagedTask(node, ignored -> finishedCount.incrementAndGet());

        task.run();
        task.run();

        assertEquals(1, startCount.get());
        assertEquals(1, closeCount.get());
        assertEquals(1, finishedCount.get());
    }

    @Test
    void rejectsSchedulingAfterShutdown() {
        ThreadTaskScheduler scheduler = new ThreadTaskScheduler();
        scheduler.shutdown();

        assertThatThrownBy(() -> scheduler.schedule(new TestPattern(false, () -> {}, () -> {})))
            .isInstanceOf(IllegalStateException.class);
    }

    private record TestPattern(boolean edt, Runnable start, Runnable close) implements NodePattern {
        @Override
        public Runnable onStart() {
            return start;
        }

        @Override
        public Runnable onClose() {
            return close;
        }

        @Override
        public Integer level() {
            return 1;
        }

        @Override
        public boolean isEDT() {
            return edt;
        }
    }
}
