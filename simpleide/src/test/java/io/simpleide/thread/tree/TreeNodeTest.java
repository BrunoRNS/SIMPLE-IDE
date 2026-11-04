package io.simpleide.thread.tree;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.nodes.NodePattern;

class TreeNodeTest {

    @Test
    void snapshotsPreserveNodeIdentityAndCopyTheTreeStructure() {
        NodePattern rootPattern = new TestPattern();
        NodePattern childPattern = new TestPattern();
        TreeNode root = new TreeNode(rootPattern);
        TreeNode child = new TreeNode(childPattern);
        root.addChild(child);

        TreeNode snapshot = root.snapshot();

        assertSame(rootPattern, snapshot.getNode());
        assertNotSame(root.getChildren().get(0), snapshot.getChildren().get(0));
        assertSame(childPattern, snapshot.getChildren().get(0).getNode());
        assertThatThrownBy(() -> snapshot.getChildren().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertTrue(root.hasChildren());
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
