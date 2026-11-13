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
import java.util.concurrent.locks.ReentrantLock;

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
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    protected AbstractThreadMaster(NodePattern root) {
        tree = new ThreadTreeRegistry(root);
        scheduler = new ThreadTaskScheduler();
    }

    public void register(NodePattern node) {
        lifecycleLock.lock();
        try {
            ensureRunning();
            tree.addNode(node);
            if (initialized.get()) {
                scheduler.schedule(node);
            }
        } finally {
            lifecycleLock.unlock();
        }
        LOGGER.debug("Registered thread node id={} level={}", node.getId(), node.level());
    }

    public ManagedThreadPool createThreadPool(String name, int workerCount) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Thread pool name must not be blank.");
        }
        if (workerCount < 1) {
            throw new IllegalArgumentException("Thread pool worker count must be positive.");
        }

        synchronized (this) {
            ensureRunning();
            RunnableNode poolNode = new RunnableNode(tree.getRoot().getNode(), () -> {});
            tree.addNode(poolNode);
            try {
                scheduler.registerDedicatedPool(poolNode, name, workerCount);
            } catch (RuntimeException exception) {
                scheduler.cancel(tree.removeNodeCascadeAndCollect(poolNode));
                throw exception;
            }
            LOGGER.info("Registered managed thread pool '{}' with {} worker(s).", name, workerCount);
            return new ManagedThreadPool(this, poolNode, name);
        }
    }

    NodePattern executeOnPool(ManagedThreadPool pool, Runnable action, Runnable onClose) {
        if (action == null) {
            throw new IllegalArgumentException("Thread pool action must not be null.");
        }
        if (pool == null || pool.owner() != this) {
            throw new IllegalArgumentException("Thread pool must belong to this ThreadMaster.");
        }

        class PoolTaskNode extends RunnableNode {
            private PoolTaskNode() {
                super(pool.node(), (Runnable) null, onClose);
            }

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
        }

        PoolTaskNode task = new PoolTaskNode();
        synchronized (this) {
            ensureRunning();
            if (!tree.contains(pool.node())) {
                throw new IllegalStateException("Thread pool '" + pool.name() + "' has been closed.");
            }
            tree.addNode(task);
            try {
                if (!initialized) {
                    initialize();
                } else {
                    scheduler.schedule(task);
                }
            } catch (RuntimeException exception) {
                scheduler.cancel(tree.removeNodeCascadeAndCollect(task));
                throw exception;
            }
        }
        return task;
    }

    void closePool(ManagedThreadPool pool) {
        if (pool.owner() != this) {
            throw new IllegalArgumentException("Thread pool must belong to this ThreadMaster.");
        }
        removeNodeCascade(pool.node());
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
        lifecycleLock.lock();
        try {
            ensureRunning();
            tree.addNode(taskNode);
            if (initialized.get()) {
                scheduler.schedule(taskNode);
            } else {
                initialize();
            }
        } finally {
            lifecycleLock.unlock();
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
        lifecycleLock.lock();
        try {
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
        } finally {
            lifecycleLock.unlock();
        }
    }

    public void shutdown() {
        lifecycleLock.lock();
        try {
            if (!shutdown.compareAndSet(false, true)) {
                return;
            }
        } finally {
            lifecycleLock.unlock();
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
        lifecycleLock.lock();
        try {
            ensureRunning();
            tree.addNode(node);
        } finally {
            lifecycleLock.unlock();
        }
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
