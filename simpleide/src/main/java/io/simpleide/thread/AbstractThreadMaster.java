package io.simpleide.thread;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.Node;
import io.simpleide.thread.nodes.NodePattern;
import io.simpleide.thread.tree.Tree;
import io.simpleide.thread.tree.TreeNode;

public abstract class AbstractThreadMaster implements Tree {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractThreadMaster.class);
    private static final int MAX_QUEUED_TASKS = 256;
    private static final int WORKER_COUNT =
        Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));

    protected TreeNode root;
    private final Map<Integer, TreeNode> nodes = new LinkedHashMap<>();
    private final Map<Integer, ManagedTask> activeTasks = new LinkedHashMap<>();
    private final ThreadPoolExecutor executor;
    private boolean initialized;
    private boolean shutdown;

    public AbstractThreadMaster(NodePattern root) {
        if (root == null || root.level() == null || root.level() != 0) {
            throw new IllegalArgumentException("The thread tree root must have level 0.");
        }
        this.root = new TreeNode(root);
        nodes.put(root.getId(), this.root);
        executor = new ThreadPoolExecutor(
            WORKER_COUNT,
            WORKER_COUNT,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_TASKS),
            new ManagedThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void register(NodePattern node) {
        synchronized (this) {
            ensureRunning();
            addNodeLocked(node);
            if (initialized) {
                scheduleLocked(node);
            }
            LOGGER.debug("Registered thread node id={} level={}", node.getId(), node.level());
        }
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
            addNodeLocked(taskNode);
            if (initialized) {
                scheduleLocked(taskNode);
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
                WORKER_COUNT,
                MAX_QUEUED_TASKS
            );
            for (TreeNode treeNode : breadthFirstNodes()) {
                scheduleLocked(treeNode.getNode());
            }
            LOGGER.info("Thread manager initialized with {} registered node(s).", nodes.size());
        }
    }

    public void shutdown() {
        List<ManagedTask> tasks;
        synchronized (this) {
            if (shutdown) {
                return;
            }
            shutdown = true;
            tasks = new ArrayList<>(activeTasks.values());
            activeTasks.clear();
        }
        LOGGER.info("Shutting down thread manager; cancelling {} active node(s).", tasks.size());
        tasks.forEach(ManagedTask::cancel);
        executor.shutdownNow();
    }

    public TreeNode getRoot() {
        synchronized (this) {
            return this.root.snapshot();
        }
    }

    public LinkedList<Integer> bfs(TreeNode node, LinkedList<Integer> result) {
        if (node == null || result == null) {
            throw new IllegalArgumentException("Node and result must not be null.");
        }
        LinkedList<TreeNode> queue = new LinkedList<>();
        queue.add(node);
        while (!queue.isEmpty()) {
            TreeNode current = queue.poll();
            result.add(current.getId());
            for (TreeNode child : current.getChildren()) {
                queue.add(child);
            }
        }
        return result;
    }

    public LinkedList<Integer> dfs_preorder(TreeNode node, LinkedList<Integer> result) {
        if (node == null || result == null) {
            throw new IllegalArgumentException("Node and result must not be null.");
        }
        result.add(node.getId());
        for (TreeNode child : node.getChildren()) {
            dfs_preorder(child, result);
        }
        return result;
    }

    public LinkedList<Integer> dfs_inorder(TreeNode node, LinkedList<Integer> result) {
        if (node == null || result == null) {
            throw new IllegalArgumentException("Node and result must not be null.");
        }
        if (node.hasChildren()) {
            dfs_inorder(node.getChildren().get(0), result);
        }
        result.add(node.getId());
        for (int i = 1; i < node.getChildren().size(); i++) {
            dfs_inorder(node.getChildren().get(i), result);
        }
        return result;
    }

    public LinkedList<Integer> dfs_postorder(TreeNode node, LinkedList<Integer> result) {
        if (node == null || result == null) {
            throw new IllegalArgumentException("Node and result must not be null.");
        }
        for (TreeNode child : node.getChildren()) {
            dfs_postorder(child, result);
        }
        result.add(node.getId());
        return result;
    }

    @Override
    public void addNode(NodePattern node) {
        synchronized (this) {
            ensureRunning();
            addNodeLocked(node);
        }
    }

    @Override
    public void removeNodeCascade(NodePattern node) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null.");
        }
        List<ManagedTask> tasksToCancel = new ArrayList<>();
        synchronized (this) {
            int id = node.getId();
            TreeNode treeNode = nodes.get(id);
            if (treeNode == null || treeNode.getNode() != node) {
                return;
            }
            if (treeNode == root) {
                throw new IllegalArgumentException("The thread tree root cannot be removed.");
            }
            List<TreeNode> removed = new ArrayList<>();
            collectSubtree(treeNode, removed);
            TreeNode parent = findParent(root, treeNode);
            if (parent != null) {
                parent.removeChild(treeNode);
            }
            for (TreeNode removedNode : removed) {
                nodes.remove(removedNode.getId());
                ManagedTask task = activeTasks.remove(removedNode.getId());
                if (task != null) {
                    tasksToCancel.add(task);
                }
            }
        }
        tasksToCancel.forEach(ManagedTask::cancel);
    }

    @Override
    public Map<Integer, TreeNode> showTree() {
        synchronized (this) {
            Map<Integer, TreeNode> snapshot = new LinkedHashMap<>();
            TreeNode snapshotRoot = root.snapshot();
            LinkedList<TreeNode> queue = new LinkedList<>();
            queue.add(snapshotRoot);
            while (!queue.isEmpty()) {
                TreeNode node = queue.removeFirst();
                snapshot.put(node.getId(), node);
                queue.addAll(node.getChildren());
            }
            return Collections.unmodifiableMap(snapshot);
        }
    }

    private void addNodeLocked(NodePattern node) {
        if (node == null) {
            throw new IllegalArgumentException("Node must not be null.");
        }
        int id = node.getId();
        TreeNode existing = nodes.get(id);
        if (existing != null) {
            if (existing.getNode() != node) {
                throw new IllegalStateException("A different node already uses ID " + id + ".");
            }
            return;
        }

        Integer level = node.level();
        if (level == null || level < 1) {
            throw new IllegalArgumentException("Every non-root node must have a positive level.");
        }
        TreeNode parent = findParentFor(node, level);
        if (parent == null || parent.getNode().level() == null
                || level != parent.getNode().level() + 1) {
            throw new IllegalArgumentException("Node level must be exactly one greater than its parent.");
        }

        TreeNode treeNode = new TreeNode(node);
        parent.addChild(treeNode);
        nodes.put(id, treeNode);
    }

    private TreeNode findParentFor(NodePattern node, int level) {
        if (node instanceof Node managedNode) {
            int parentId = managedNode.getSuperNode().id();
            TreeNode parent = nodes.get(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("The node's parent must be registered first.");
            }
            return parent;
        }
        if (level == 1) {
            return root;
        }
        TreeNode match = null;
        for (TreeNode candidate : nodes.values()) {
            if (candidate.getNode().level() != null && candidate.getNode().level() == level - 1) {
                if (match != null) {
                    throw new IllegalArgumentException(
                        "A non-Node NodePattern at level " + level + " needs an explicit parent."
                    );
                }
                match = candidate;
            }
        }
        return match;
    }

    private void scheduleLocked(NodePattern node) {
        int id = node.getId();
        if (activeTasks.containsKey(id)) {
            return;
        }
        ManagedTask task = new ManagedTask(node);
        activeTasks.put(id, task);
        if (node.isEDT()) {
            EventQueue.invokeLater(task);
        } else {
            try {
                executor.execute(task);
            } catch (RuntimeException exception) {
                activeTasks.remove(id, task);
                LOGGER.error(
                    "Unable to schedule thread node id={}; queue contains {} task(s).",
                    id,
                    executor.getQueue().size(),
                    exception
                );
                throw exception;
            }
        }
    }

    private List<TreeNode> breadthFirstNodes() {
        List<TreeNode> result = new ArrayList<>();
        LinkedList<TreeNode> queue = new LinkedList<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TreeNode current = queue.removeFirst();
            result.add(current);
            queue.addAll(current.getChildren());
        }
        return result;
    }

    private static void collectSubtree(TreeNode node, List<TreeNode> result) {
        result.add(node);
        for (TreeNode child : node.getChildren()) {
            collectSubtree(child, result);
        }
    }

    private static TreeNode findParent(TreeNode parent, TreeNode target) {
        if (parent.getChildren().contains(target)) {
            return parent;
        }
        for (TreeNode child : parent.getChildren()) {
            TreeNode found = findParent(child, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void ensureRunning() {
        if (shutdown) {
            throw new IllegalStateException("ThreadMaster has been shut down.");
        }
    }

    private final class ManagedTask implements Runnable {
        private final NodePattern node;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile boolean cancelled;
        private volatile Thread runner;

        private ManagedTask(NodePattern node) {
            this.node = node;
        }

        @Override
        public void run() {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            runner = Thread.currentThread();
            try {
                if (!cancelled) {
                    Runnable action = node.onStart();
                    if (action != null) {
                        action.run();
                    }
                }
            } catch (RuntimeException | Error exception) {
                LOGGER.error("Managed thread node id={} failed.", node.getId(), exception);
                throw exception;
            } finally {
                runner = null;
                closeOnce();
                synchronized (AbstractThreadMaster.this) {
                    activeTasks.remove(node.getId(), this);
                }
            }
        }

        private void cancel() {
            cancelled = true;
            Thread currentRunner = runner;
            if (currentRunner != null && !node.isEDT()) {
                currentRunner.interrupt();
            } else if (!started.get()) {
                if (!node.isEDT()) {
                    executor.remove(this);
                }
                closeOnce();
            }
        }

        private void closeOnce() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            Runnable closeAction;
            try {
                closeAction = node.onClose();
            } catch (RuntimeException | Error exception) {
                LOGGER.error("Unable to create close action for node id={}.", node.getId(), exception);
                return;
            }
            if (closeAction == null) {
                return;
            }
            Runnable guardedClose = () -> {
                try {
                    closeAction.run();
                } catch (RuntimeException | Error exception) {
                    LOGGER.error("Close action for node id={} failed.", node.getId(), exception);
                }
            };
            if (node.isEDT() && !EventQueue.isDispatchThread()) {
                EventQueue.invokeLater(guardedClose);
            } else {
                guardedClose.run();
            }
        }
    }

    private static final class ManagedThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "simpleide-thread-" + ++sequence);
            thread.setDaemon(true);
            LOGGER.debug("Created managed worker {}.", thread.getName());
            return thread;
        }
    }
}
