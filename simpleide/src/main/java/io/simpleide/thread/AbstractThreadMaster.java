package io.simpleide.thread;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    private boolean initialized;
    private boolean shutdown;

    protected AbstractThreadMaster(NodePattern root) {
        tree = new ThreadTreeRegistry(root);
        scheduler = new ThreadTaskScheduler();
    }

    public void register(NodePattern node) {
        synchronized (this) {
            ensureRunning();
            tree.addNode(node);
            if (initialized) {
                scheduler.schedule(node);
            }
            LOGGER.debug("Registered thread node id={} level={}", node.getId(), node.level());
        }
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
        synchronized (this) {
            ensureRunning();
            tree.addNode(taskNode);
            if (initialized) {
                scheduler.schedule(taskNode);
            } else {
                initialize();
            }
        }
        return taskNode;
    }

    public NodePattern executeOnEDT(Runnable action) {
        return executeOnEDT(action, null);
    }

    public void initialize() {
        synchronized (this) {
            ensureRunning();
            if (initialized) {
                return;
            }
            initialized = true;
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
    }

    public void shutdown() {
        synchronized (this) {
            if (shutdown) {
                return;
            }
            shutdown = true;
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
    public synchronized Boolean isNodeRunning(NodePattern node) {
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
        synchronized (this) {
            ensureRunning();
            tree.addNode(node);
        }
    }

    @Override
    public void removeNodeCascade(NodePattern node) {
        List<NodePattern> removedNodes;
        synchronized (this) {
            removedNodes = tree.removeNodeCascadeAndCollect(node);
        }
        scheduler.cancel(removedNodes);
    }

    @Override
    public Map<BigInteger, TreeNode> showTree() {
        return tree.showTree();
    }

    private void ensureRunning() {
        if (shutdown) {
            throw new IllegalStateException("ThreadMaster has been shut down.");
        }
    }
}
