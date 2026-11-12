package io.simpleide.thread;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.nodes.runnable.RunnableNode;
import io.simpleide.thread.tree.ThreadTreeRegistry;
import io.simpleide.thread.tree.Tree;
import io.simpleide.thread.tree.TreeNode;

public abstract class AbstractThreadMaster implements Tree {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractThreadMaster.class);

    private final ThreadTreeRegistry tree;
    private final ThreadTaskScheduler scheduler;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    protected AbstractThreadMaster(NodePattern root) {
        tree = new ThreadTreeRegistry(root);
        scheduler = new ThreadTaskScheduler();
    }

    public void register(NodePattern node) {
        ensureRunning();
        tree.addNode(node);
        if (initialized.get()) {
            scheduler.schedule(node);
        }
        LOGGER.debug("Registered thread node id={} level={}", node.getId(), node.level());
    }

    public NodePattern executeOnEDT(Runnable action, Runnable onClose) {
        if (action == null) {
            throw new IllegalArgumentException("EDT action must not be null.");
        }
        NodePattern taskNode = new NodePattern() {
            @Override
            public Runnable onStart() {
                return () -> {
                    try {
                        action.run();
                    } finally {
                        removeNodeCascade(this);
                    }
                };
            }

            @Override
            public Runnable onClose() {
                return onClose;
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
        ensureRunning();
        tree.addNode(taskNode);
        if (!initialized.get()) {
            initialize();
        }
        if (initialized.get()) {
            scheduler.schedule(taskNode);
        }
        return taskNode;
    }

    public NodePattern executeOnEDT(Runnable action) {
        return executeOnEDT(action, null);
    }

    public NodePattern execute(NodePattern parent, Runnable action) {
        if (parent == null || action == null) {
            throw new IllegalArgumentException("Task parent and action must not be null.");
        }
        ensureRunning();
        if (!tree.contains(parent)) {
            throw new IllegalArgumentException("Task parent must be registered with ThreadMaster.");
        }
        AtomicReference<RunnableNode> taskReference = new AtomicReference<>();
        RunnableNode task = new RunnableNode(parent, action, () -> removeNodeCascade(taskReference.get()));
        taskReference.set(task);
        register(task);
        return task;
    }

    public ScheduledFuture<?> schedule(
            NodePattern parent,
            Runnable action,
            long delay,
            TimeUnit unit) {
        if (parent == null || action == null || unit == null) {
            throw new IllegalArgumentException("Task parent, action, and time unit must not be null.");
        }
        ensureRunning();
        if (!tree.contains(parent)) {
            throw new IllegalArgumentException("Task parent must be registered with ThreadMaster.");
        }
        return scheduler.schedule(() -> {
            try {
                execute(parent, action);
            } catch (RuntimeException error) {
                LOGGER.error("Unable to schedule delayed thread node.", error);
            }
        }, delay, unit);
    }

    public ExecutorService createExecutorService(NodePattern parent) {
        ensureRunning();
        if (!tree.contains(parent)) {
            throw new IllegalArgumentException("Executor parent must be registered with ThreadMaster.");
        }
        return new ThreadMasterExecutorService(this, parent);
    }

    public void initialize() {
        ensureRunning();
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        LOGGER.info(
            "Initializing thread manager with {} worker(s) and queue capacity {}.",
            ThreadTaskScheduler.WORKER_COUNT,
            ThreadTaskScheduler.MAX_QUEUED_TASKS
        );
        for (NodePattern node : tree.breadthFirstNodes()) {
            scheduler.schedule(node);
        }
        LOGGER.info("Thread manager initialized with {} registered node(s).", tree.size());
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdown();
    }

    /**
     * Returns whether a registered node is currently executing.
     *
     * @param node node to inspect
     * @return {@code null} if the node is not registered, {@code true} while
     *         its task is executing, or {@code false} otherwise
     */
    public Boolean isNodeRunning(NodePattern node) {
        if (!tree.contains(node)) {
            return null;
        }
        return scheduler.isRunning(node.getId());
    }

    public TreeNode getRoot() {
        return tree.getRoot();
    }

    public LinkedList<BigInteger> bfs(TreeNode node, LinkedList<BigInteger> result) {
        return tree.bfs(node, result);
    }

    public LinkedList<BigInteger> dfs_preorder(TreeNode node, LinkedList<BigInteger> result) {
        return tree.dfsPreorder(node, result);
    }

    public LinkedList<BigInteger> dfs_inorder(TreeNode node, LinkedList<BigInteger> result) {
        return tree.dfsInorder(node, result);
    }

    public LinkedList<BigInteger> dfs_postorder(TreeNode node, LinkedList<BigInteger> result) {
        return tree.dfsPostorder(node, result);
    }

    @Override
    public void addNode(NodePattern node) {
        ensureRunning();
        tree.addNode(node);
    }

    @Override
    public void removeNodeCascade(NodePattern node) {
        List<NodePattern> removedNodes = tree.removeNodeCascadeAndCollect(node);
        scheduler.cancel(removedNodes);
    }

    @Override
    public Map<BigInteger, TreeNode> showTree() {
        return tree.showTree();
    }

    private void ensureRunning() {
        if (shutdown.get()) {
            throw new IllegalStateException("ThreadMaster has been shut down.");
        }
    }
}
