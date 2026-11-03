package io.simpleide.thread.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodePatternTest {

    @Test
    void assignsStableIdentityIdsPerNodeInstance() {
        NodePattern first = new TestPattern();
        NodePattern second = new TestPattern();

        int firstId = first.getId();
        assertEquals(firstId, first.getId());
        assertNotEquals(firstId, second.getId());
        assertTrue(firstId > 0);
    }

    private static final class TestPattern implements NodePattern {
        @Override
        public Runnable onStart() {
            return null;
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
    }
}
