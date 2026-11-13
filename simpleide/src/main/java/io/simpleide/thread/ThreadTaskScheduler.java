package io.simpleide.thread;

import java.awt.EventQueue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.thread.nodes.Node;
import io.simpleide.thread.nodes.NodePattern;

final class ThreadTaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadTaskScheduler.class);
    static final int MAX_QUEUED_TASKS = 256;
    static final int WORKER_COUNT =
        Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));

    private final Map<BigInteger, ManagedTask> activeTasks = new LinkedHashMap<>();
    private final Map<BigInteger, ThreadPoolExecutor> taskExecutors = new HashMap<>();
    private final Map<BigInteger, ThreadPoolExecutor> dedicatedExecutors = new HashMap<>();
    private final Set<BigInteger> scheduledIds = new HashSet<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_QUEUED_TASKS),
        new ManagedThreadFactory("simpleide-thread-"),
        new ThreadPoolExecutor.AbortPolicy()
    );
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(
        1,
        new ManagedThreadFactory("simpleide-timer-")
    );
    private boolean shutdown;

    ThreadTaskScheduler() {
        timer.setRemoveOnCancelPolicy(true);
    }

    void schedule(NodePattern node) {
        BigInteger id = node.getId();
        ManagedTask task;
        synchronized (this) {
            if (shutdown) {
                throw new IllegalStateException("Thread task scheduler has been shut down.");
            }
            if (!scheduledIds.add(id)) {
                return;
            }
            task = new ManagedTask(node, this::onTaskFinished);
            activeTasks.put(id, task);
        if (dedicatedExecutors.containsKey(id)) {
            return;
        }
        if (!scheduledIds.add(id)) {
            return;
        }
        if (node.isEDT()) {
            EventQueue.invokeLater(task);
            return;
        }
        ThreadPoolExecutor taskExecutor = executorFor(node);
        taskExecutors.put(id, taskExecutor);
        try {
            taskExecutor.execute(task);
        } catch (RuntimeException exception) {
            synchronized (this) {
                activeTasks.remove(id, task);
                scheduledIds.remove(id);
            }
            LOGGER.error(
                "Unable to schedule thread node id={}; queue contains {} task(s).",
                id,
                taskExecutor.getQueue().size(),
                exception
            );
            throw exception;
        }
    }

    synchronized void registerDedicatedPool(NodePattern poolNode, String name, int workerCount) {
        if (shutdown) {
            throw new IllegalStateException("Thread task scheduler has been shut down.");
        }
        if (workerCount < 1) {
            throw new IllegalArgumentException("Thread pool worker count must be positive.");
        }
        BigInteger id = poolNode.getId();
        if (dedicatedExecutors.containsKey(id)) {
            throw new IllegalStateException("A thread pool is already registered for node " + id + ".");
        }
        String threadPrefix = "simpleide-" + name + "-" + id + "-worker-";
        ThreadPoolExecutor dedicatedExecutor = new ThreadPoolExecutor(
            workerCount,
            workerCount,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_TASKS),
            new ManagedThreadFactory(threadPrefix),
            new ThreadPoolExecutor.AbortPolicy()
        );
        dedicatedExecutors.put(id, dedicatedExecutor);
    }

    synchronized boolean isRunning(BigInteger nodeId) {
        ManagedTask task = activeTasks.get(nodeId);
        return task != null && task.isRunning();
    }

    synchronized ScheduledFuture<?> schedule(Runnable action, long delay, TimeUnit unit) {
        if (shutdown) {
            throw new IllegalStateException("Thread task scheduler has been shut down.");
        }
        return timer.schedule(action, delay, unit);
    }

    void cancel(Collection<NodePattern> nodes) {
        List<ScheduledTask> tasksToCancel = new ArrayList<>();
        List<ThreadPoolExecutor> poolsToShutdown = new ArrayList<>();
        synchronized (this) {
            for (NodePattern node : nodes) {
                BigInteger id = node.getId();
                scheduledIds.remove(id);
                ManagedTask task = activeTasks.remove(id);
                if (task != null) {
                    tasksToCancel.add(new ScheduledTask(task, taskExecutors.remove(id)));
                }
                ThreadPoolExecutor pool = dedicatedExecutors.remove(id);
                if (pool != null) {
                    poolsToShutdown.add(pool);
                }
            }
        }
        tasksToCancel.forEach(ScheduledTask::cancel);
        poolsToShutdown.forEach(ThreadPoolExecutor::shutdown);
    }

    void shutdown() {
        List<ScheduledTask> tasks;
        List<ThreadPoolExecutor> pools;
        synchronized (this) {
            if (shutdown) {
                return;
            }
            shutdown = true;
            tasks = new ArrayList<>(activeTasks.size());
            for (ManagedTask task : activeTasks.values()) {
                tasks.add(new ScheduledTask(task, taskExecutors.get(task.getNodeId())));
            }
            activeTasks.clear();
            taskExecutors.clear();
            scheduledIds.clear();
            pools = new ArrayList<>(dedicatedExecutors.values());
            dedicatedExecutors.clear();
        }
        LOGGER.info("Shutting down thread manager; cancelling {} active node(s).", tasks.size());
        tasks.forEach(task -> this.cancel(task));
        timer.shutdownNow();
        executor.shutdownNow();
    }

    private ThreadPoolExecutor executorFor(NodePattern node) {
        if (node instanceof Node managedNode) {
            ThreadPoolExecutor dedicatedExecutor = dedicatedExecutors.get(managedNode.getSuperNode().getId());
            if (dedicatedExecutor != null) {
                return dedicatedExecutor;
            }
        }
        return executor;
    }

    private synchronized void onTaskFinished(ManagedTask task) {
        if (activeTasks.remove(task.getNodeId(), task)) {
            taskExecutors.remove(task.getNodeId());
        }
    }

    private record ScheduledTask(ManagedTask task, ThreadPoolExecutor executor) {
        private void cancel() {
            task.cancel(() -> {
                if (executor != null) {
                    executor.remove(task);
                }
            });
        }
    }

    private static final class ManagedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private int sequence;

        private ManagedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, namePrefix + ++sequence);
            thread.setDaemon(true);
            LOGGER.debug("Created managed worker {}.", thread.getName());
            return thread;
        }
    }
}
