package io.simpleide.thread.nodes.edt.listeners;

import java.awt.event.ActionEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.ThreadMaster;

class EDTNodeListenerTest {

    private static final int TEST_TIMEOUT_SECONDS = 2;

    @BeforeAll
    static void initializeThreadManager() {
        ThreadMaster.getInstance().initialize();
    }

    @Test
    void actionNodeRegistersAndRunsCallbacksOnEdtWithItsEvent() throws Exception {
        ThreadMaster master = ThreadMaster.getInstance();
        int treeSizeBefore = master.showTree().size();
        Object source = new Object();
        ActionEvent event = new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "execute");
        AtomicReference<ActionEvent> startedEvent = new AtomicReference<>();
        AtomicReference<ActionEvent> closedEvent = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        ActionNode node = new ActionNode(
            received -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                assertThat(master.showTree()).hasSize(treeSizeBefore + 1);
                startedEvent.set(received);
                started.countDown();
            },
            received -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                closedEvent.set(received);
                closed.countDown();
            }
        );

        SwingUtilities.invokeAndWait(() -> node.actionPerformed(event));

        assertThat(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(master.showTree()).hasSize(treeSizeBefore);
        assertThat(startedEvent.get()).isSameAs(event);
        assertThat(closedEvent.get()).isSameAs(event);
    }

    @Test
    void changeNodeRegistersAndRunsCallbacksOnEdtWithItsEvent() throws Exception {
        ThreadMaster master = ThreadMaster.getInstance();
        int treeSizeBefore = master.showTree().size();
        Object source = new Object();
        ChangeEvent event = new ChangeEvent(source);
        AtomicReference<ChangeEvent> startedEvent = new AtomicReference<>();
        AtomicReference<ChangeEvent> closedEvent = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        ChangeNode node = new ChangeNode(
            received -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                assertThat(master.showTree()).hasSize(treeSizeBefore + 1);
                startedEvent.set(received);
                started.countDown();
            },
            received -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                closedEvent.set(received);
                closed.countDown();
            }
        );

        SwingUtilities.invokeAndWait(() -> node.stateChanged(event));

        assertThat(started.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(closed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        assertThat(master.showTree()).hasSize(treeSizeBefore);
        assertThat(startedEvent.get()).isSameAs(event);
        assertThat(closedEvent.get()).isSameAs(event);
    }
}
