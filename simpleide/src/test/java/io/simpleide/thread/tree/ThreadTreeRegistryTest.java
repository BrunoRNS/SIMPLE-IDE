package io.simpleide.thread.tree;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.runnable.RunnableNode;

class ThreadTreeRegistryTest {

    @Test
    void providesTreeTraversalsAndAnImmutableSnapshot() {
        NodePattern root = new TestPattern(0);
        ThreadTreeRegistry tree = new ThreadTreeRegistry(root);
        RunnableNode first = new RunnableNode(root, () -> {});
        RunnableNode child = new RunnableNode(first, () -> {});
        RunnableNode second = new RunnableNode(root, () -> {});

        tree.addNode(first);
        tree.addNode(child);
        tree.addNode(second);
        tree.addNode(first);

        TreeNode rootSnapshot = tree.getRoot();
        assertEquals(
            List.of(root.getId(), first.getId(), second.getId(), child.getId()),
            tree.bfs(rootSnapshot, new LinkedList<>())
        );
        assertEquals(
            List.of(root.getId(), first.getId(), child.getId(), second.getId()),
            tree.dfsPreorder(rootSnapshot, new LinkedList<>())
        );
        assertEquals(
            List.of(child.getId(), first.getId(), root.getId(), second.getId()),
            tree.dfsInorder(rootSnapshot, new LinkedList<>())
        );
        assertEquals(
            List.of(child.getId(), first.getId(), second.getId(), root.getId()),
            tree.dfsPostorder(rootSnapshot, new LinkedList<>())
        );
        assertEquals(4, tree.size());
        assertEquals(4, tree.showTree().size());
        assertThatThrownBy(() -> tree.showTree().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void implementsTheTreeRegistrationContract() {
        NodePattern root = new TestPattern(0);
        Tree tree = new ThreadTreeRegistry(root);
        RunnableNode node = new RunnableNode(root, () -> {});

        tree.addNode(node);

        assertTrue(tree.showTree().containsKey(node.getId()));
        tree.removeNodeCascade(node);
        assertFalse(tree.showTree().containsKey(node.getId()));
    }

    @Test
    void removesSubtreesAndRejectsRemovingRootOrAddingAnUnregisteredParent() {
        NodePattern root = new TestPattern(0);
        ThreadTreeRegistry tree = new ThreadTreeRegistry(root);
        RunnableNode parent = new RunnableNode(root, () -> {});
        RunnableNode child = new RunnableNode(parent, () -> {});
        tree.addNode(parent);
        tree.addNode(child);

        List<NodePattern> removed = tree.removeNodeCascadeAndCollect(parent);

        assertEquals(List.of(parent, child), removed);
        assertEquals(1, tree.size());
        assertFalse(tree.showTree().containsKey(parent.getId()));
        assertFalse(tree.showTree().containsKey(child.getId()));
        assertThatThrownBy(() -> tree.removeNodeCascade(root))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tree.addNode(new RunnableNode(new TestPattern(0), () -> {})))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidRootsAndInconsistentLevels() {
        assertThatThrownBy(() -> new ThreadTreeRegistry(new TestPattern(1)))
            .isInstanceOf(IllegalArgumentException.class);
        NodePattern root = new TestPattern(0);
        ThreadTreeRegistry tree = new ThreadTreeRegistry(root);
        assertThatThrownBy(() -> tree.addNode(new TestPattern(3)))
            .isInstanceOf(IllegalArgumentException.class);
        assertTrue(tree.showTree().containsKey(root.getId()));
    }

    private static final class TestPattern implements NodePattern {
        private final Integer level;

        private TestPattern(Integer level) {
            this.level = level;
        }

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
            return level;
        }

        @Override
        public boolean isEDT() {
            return false;
        }
    }
}
