package io.simpleide.thread.nodes.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.simpleide.thread.nodes.NodePattern;
import org.junit.jupiter.api.Test;

class NodeRecordFactoryTest {

  @Test
  void snapshotsNodeCallbacksLevelAndId() {
    Runnable start = () -> {};
    Runnable close = () -> {};
    NodePattern node =
        new NodePattern() {
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
            return true;
          }
        };

    NodeRecord record = NodeRecordFactory.create(node);

    assertSame(start, record.onStart());
    assertSame(close, record.onClose());
    assertEquals(1, record.level());
    assertEquals(node.getId(), record.id());
  }
}
