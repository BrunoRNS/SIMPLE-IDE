# Threading API

`io.simpleide.thread.ThreadMaster` is a process-wide thread tree. It provides:

- A bounded background worker pool.
- Swing Event Dispatch Thread (EDT) task scheduling.
- Named, dedicated worker pools.
- Scheduled tasks.
- A tree of nodes that can be cancelled as a group.

`io.simpleide.api.Threads` is the small facade most embedding applications
should use.

## Get the manager

```java
import io.simpleide.api.Threads;
import io.simpleide.thread.ThreadMaster;

ThreadMaster master = Threads.master();
```

`Threads.master()` initializes the singleton on first use. The application uses
the same instance, so language processes, terminal sessions and your tasks share
one manager.

## Run a background task

```java
import io.simpleide.api.Threads;

Threads.background(
    () -> {
      String result = readLargeFile();
      Threads.onEdt(() -> label.setText(result));
    },
    () -> System.out.println("Task closed"));
```

`background(action)` returns a `NodePattern`. `background(action, onClose)` adds
a close callback. The callback runs when the node finishes, is cancelled, or the
manager shuts down.

Both methods reject a `null` action with a `NullPointerException` before any
node is created, so a failed submission never leaves state behind.

Background tasks run on the shared pool:

| Property | Value |
| --- | --- |
| Worker count | `max(4, min(8, availableProcessors))` |
| Queue capacity | 256 |
| Rejection policy | Abort with `RejectedExecutionException` |
| Thread naming | `simpleide-thread-N` |
| Daemon threads | Yes |

Do not submit unbounded work. If the queue is full, submission throws
`RejectedExecutionException`. Add backpressure or use a dedicated pool.

## Run a task on the EDT

```java
import io.simpleide.api.Threads;

Threads.onEdt(() -> {
  statusLabel.setText("Ready");
});
```

`onEdt` always schedules through Swing's event queue, even if the calling thread
is already the EDT. Use it for all Swing mutations from background tasks.

The optional close callback runs after the action finishes:

```java
Threads.onEdt(action, () -> System.out.println("UI update closed"));
```

## Create a dedicated pool

```java
import io.simpleide.thread.ManagedThreadPool;
import io.simpleide.api.Threads;

try (ManagedThreadPool pool = Threads.pool("imports", 2)) {
  pool.execute(() -> importFile("a.txt"));
  pool.execute(() -> importFile("b.txt"));
}
```

Each pool has its own fixed worker count and a 256-entry queue. Tasks submitted
through `pool.execute` run only on that pool. Closing the pool removes its node
and interrupts running tasks. The terminal controller uses this mechanism.

## Schedule a delayed task

```java
import io.simpleide.api.Threads;
import java.util.concurrent.TimeUnit;

Threads.schedule(() -> System.out.println("later"), 2, TimeUnit.SECONDS);
```

The scheduler is a single daemon timer thread. The delayed action is executed as
a background node when the delay expires, not on the timer thread.

## Check whether a node is running

```java
import io.simpleide.api.Threads;
import io.simpleide.thread.nodes.NodePattern;

NodePattern node = Threads.background(() -> doWork());
Boolean running = Threads.isRunning(node);
```

Returns:

- `null` when the node is not registered.
- `true` while the action is executing.
- `false` when registered but not executing.

## Register a custom node

Use `ThreadMaster.register` when you need a node type other than
`RunnableNode`:

```java
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

ThreadMaster master = Threads.master();
RunnableNode node = new RunnableNode(() -> doWork(), () -> cleanup());
master.register(node);
```

`register` schedules the node immediately when the manager is initialized. If it
is not initialized, `initialize()` schedules every registered node in breadth
order.

## Parent-child relationships and cancellation

Nodes form a tree. Removing a node removes its descendants and cancels their
queued tasks:

```java
master.removeNodeCascade(node);
```

Cancellation:

- Marks the node cancelled before it starts.
- Removes a queued task from its executor.
- Interrupts a running background task.
- Does **not** interrupt a running EDT task; it only prevents a not-yet-started
  EDT task from running.
- Runs the close callback at most once.

Use cascade removal to stop a group of related tasks, such as all work owned by a
closed editor.

## Language runner integration

`Languages.start(path)` creates an `AbstractLanguage` and calls
`startManaged()`, which registers it with the manager. The language process is
then visible in the same tree as your tasks. `Languages.stop(language)` calls
`forceStop()`.

## Threading rules

1. Never block the EDT. Use background tasks for I/O, compilation and servers.
2. Never mutate Swing components off the EDT. Use `Threads.onEdt`.
3. Do not let a background task wait for another task in the same shared pool.
   The pool can deadlock when all workers wait.
4. Use a dedicated pool for work that can block on other pool tasks.
5. Close dedicated pools when their owner goes away.
6. Do not call `Threads.shutdown()` in a library or plugin.
7. Keep tasks short enough that cancellation has a chance to take effect.
8. Check `Thread.currentThread().isInterrupted()` in long loops.

## Shutdown

```java
Threads.shutdown();
```

Shutdown:

- Cancels active nodes.
- Interrupts background runners.
- Shuts down dedicated pools and the timer.
- Shuts down the shared worker pool.
- Is irreversible for the current JVM.

The desktop application calls shutdown when it exits. An embedding application
can call it from its own shutdown hook if it owns the whole process.

## Exceptions

Exceptions thrown by a node are logged with the node id. They are not rethrown to
the caller of `background`, because the caller has already returned. Wrap task
bodies in your own try/catch when you need application-specific recovery.

## Complete example

```java
import io.simpleide.api.Threads;
import io.simpleide.thread.ManagedThreadPool;
import java.util.concurrent.TimeUnit;

public final class ThreadingDemo {
  public static void main(String[] args) {
    Threads.background(() -> System.out.println("background"));
    Threads.onEdt(() -> System.out.println("edt"));
    Threads.schedule(() -> System.out.println("delayed"), 1, TimeUnit.SECONDS);

    try (ManagedThreadPool pool = Threads.pool("demo", 2)) {
      pool.execute(() -> System.out.println("pool task"));
    }

    Threads.shutdown();
  }
}
```

`Threads.shutdown()` is appropriate here only because this demo owns the JVM.
