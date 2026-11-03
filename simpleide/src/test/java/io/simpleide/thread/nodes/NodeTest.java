package io.simpleide.thread.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.simpleide.thread.nodes.record.NodeRecord;
import io.simpleide.thread.nodes.runnable.RunnableNode;

class NodeTest {

    @Test
    void storesCallbacksAndParentWithoutInvokingParentBehavior() {
        Runnable start = () -> {};
        Runnable close = () -> {};
        TestPattern parent = new TestPattern(0);
        NodePattern actual = new TestPattern(0, start, close);

        RunnableNode node = new RunnableNode(parent, actual);

        assertSame(parent, node.getSuperNode());
        assertSame(start, node.onStart());
        assertSame(close, node.onClose());
        assertEquals(1, node.level());
        assertFalse(node.isEDT());
    }

    @Test
    void derivesLevelFromParentForRunnableNodes() {
        RunnableNode parent = new RunnableNode(new TestPattern(0), () -> {});
        RunnableNode child = new RunnableNode(parent, () -> {});

        assertEquals(1, parent.level());
        assertEquals(2, child.level());
    }

    @Test
    void rejectsParentsWithoutAUsableLevel() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RunnableNode(new TestPattern(null), () -> {})
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new RunnableNode(new TestPattern(Integer.MAX_VALUE), () -> {})
        );
    }

    @Test
    void createsRecordWithNodeCallbacksAndIdentity() {
        Runnable start = () -> {};
        Runnable close = () -> {};
        RunnableNode node = new RunnableNode(new TestPattern(0), start, close);

        NodeRecord record = node.getNodeRecord();

        assertSame(start, record.onStart());
        assertSame(close, record.onClose());
        assertEquals(node.level(), record.level());
        assertEquals(node.getId(), record.id());
    }

    private static final class TestPattern implements NodePattern {
        private final Integer level;
        private final Runnable start;
        private final Runnable close;

        private TestPattern(Integer level) {
            this(level, null, null);
        }

        private TestPattern(Integer level, Runnable start, Runnable close) {
            this.level = level;
            this.start = start;
            this.close = close;
        }

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
            return level;
        }

        @Override
        public boolean isEDT() {
            return false;
        }
    }
}
