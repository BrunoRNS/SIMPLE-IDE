package io.simpleide.thread;

import java.awt.EventQueue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
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

import io.simpleide.thread.nodes.NodePattern;

final class ThreadTaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadTaskScheduler.class);
    static final int MAX_QUEUED_TASKS = 256;
    static final int WORKER_COUNT =
        Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()));

    private final Map<BigInteger, ManagedTask> activeTasks = new LinkedHashMap<>();
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
        }
        if (node.isEDT()) {
            EventQueue.invokeLater(task);
            return;
        }
        try {
            executor.execute(task);
        } catch (RuntimeException exception) {
            synchronized (this) {
                activeTasks.remove(id, task);
                scheduledIds.remove(id);
            }
            LOGGER.error(
                "Unable to schedule thread node id={}; queue contains {} task(s).",
                id,
                executor.getQueue().size(),
                exception
            );
            throw exception;
        }
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
        List<ManagedTask> tasksToCancel = new ArrayList<>();
        synchronized (this) {
            for (NodePattern node : nodes) {
                scheduledIds.remove(node.getId());
                ManagedTask task = activeTasks.remove(node.getId());
                if (task != null) {
                    tasksToCancel.add(task);
                }
            }
        }
        tasksToCancel.forEach(this::cancel);
    }

    void shutdown() {
        List<ManagedTask> tasks;
        synchronized (this) {
            if (shutdown) {
                return;
            }
            shutdown = true;
            tasks = new ArrayList<>(activeTasks.values());
            activeTasks.clear();
            scheduledIds.clear();
        }
        LOGGER.info("Shutting down thread manager; cancelling {} active node(s).", tasks.size());
        tasks.forEach(task -> this.cancel(task));
        timer.shutdownNow();
        executor.shutdownNow();
    }

    private void cancel(ManagedTask task) {
        task.cancel(() -> executor.remove(task));
    }

    private synchronized void onTaskFinished(ManagedTask task) {
        activeTasks.remove(task.getNodeId(), task);
    }

    private static final class ManagedThreadFactory implements ThreadFactory {
        private final String prefix;
        private int sequence;

        private ManagedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = new Thread(task, prefix + ++sequence);
            thread.setDaemon(true);
            LOGGER.debug("Created managed worker {}.", thread.getName());
            return thread;
        }
    }
}
