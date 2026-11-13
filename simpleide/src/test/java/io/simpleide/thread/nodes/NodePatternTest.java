package io.simpleide.thread.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class NodePatternTest {

    @Test
    void assignsStableIdentityIdsPerNodeInstance() {
        NodePattern first = new TestPattern();
        NodePattern second = new TestPattern();

        BigInteger firstId = first.getId();
        assertEquals(firstId, first.getId());
        assertNotEquals(firstId, second.getId());
        assertTrue(firstId.signum() > 0);
    }

    @Test
    void generatesIdsBeyondThe32BitRange() {
        BigInteger start = BigInteger.ONE.shiftLeft(32);
        NodeIdGenerator generator = new NodeIdGenerator(start);

        BigInteger id = generator.nextId();

        assertEquals(start.add(BigInteger.ONE), id);
        assertTrue(id.bitLength() > 32);
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
