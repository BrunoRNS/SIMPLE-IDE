# Using the Thread Manager

`ThreadMaster` manages application tasks and maintains a tree of registered
nodes. Use its shared instance:

```java
ThreadMaster threads = ThreadMaster.getInstance();
```

The application initializes the manager when the main window starts. Call
`initialize()` once if a task must be started earlier. After initialization,
each call to `register(...)` also schedules the new node.

## Check whether a node is running

`isNodeRunning(...)` returns a nullable `Boolean`:

| Result | Meaning |
| --- | --- |
| `true` | The registered node's task is currently executing. |
| `false` | The node is registered, but its task is queued, has not started, or has finished. |
| `null` | The node is not registered in this thread manager. |

```java
Boolean running = threads.isNodeRunning(node);
if (Boolean.TRUE.equals(running)) {
    System.out.println("The task is running");
} else if (Boolean.FALSE.equals(running)) {
    System.out.println("The node is registered but not running");
} else {
    System.out.println("The node is not registered");
}
```

The check is a snapshot: a task may start or finish immediately after the
method returns.

## Button actions

`ActionNode` implements `ActionListener`. Its callback runs on the Swing Event
Dispatch Thread (EDT) and receives the `ActionEvent`:

```java
JButton save = new JButton("Save");
save.addActionListener(new ActionNode(event -> {
    System.out.println("Action: " + event.getActionCommand());
}));
```

An optional close callback can run after the action:

```java
save.addActionListener(new ActionNode(
    event -> saveFile(),
    event -> System.out.println("Action completed")
));
```

Each event creates a temporary EDT task in the tree. The manager removes it
when it finishes; the listener does not need to be registered manually.

## Update a component on the EDT

Code running in a `RunnableNode` must not update Swing components directly.
Schedule the update with `executeOnEDT`:

```java
ThreadMaster threads = ThreadMaster.getInstance();

threads.register(new RunnableNode(threads.getROOT(), () -> {
    String result = loadData();
    threads.executeOnEDT(() -> statusLabel.setText(result));
}));
```

`executeOnEDT` runs the action on the EDT and accepts an optional close
callback:

```java
threads.executeOnEDT(
    () -> statusLabel.setText("Ready"),
    () -> recordUpdateCompletion()
);
```

Tasks scheduled this way are also temporarily added to the tree and removed
after execution.

## Show a popup

Call `startManaged()` (or `run()`) from any thread; the popup is displayed on
the EDT:

```java
new InfoPopUP("Saved", "The file was saved successfully").startManaged();
```

Other popup types follow the same pattern:

```java
import io.simpleide.frame.popup.utils.ErrorTypePopUp;

new WarningPopUP("Warning", "Check the entered data").startManaged();
new ErrorPopUP("Unable to save", ErrorTypePopUp.RUNTIME_ERROR).startManaged();
new SuccessPopUP("Complete", "The operation finished").startManaged();
```

## Background work

Use `RunnableNode` for tasks that must not block the UI. Its callbacks run in
the manager's worker pool, outside the EDT:

```java
ThreadMaster threads = ThreadMaster.getInstance();

threads.register(new RunnableNode(
    threads.getROOT(),
    () -> {
        processFile();
        threads.executeOnEDT(() -> statusLabel.setText("Processing complete"));
    },
    () -> releaseResources()
));
```

Keep UI action callbacks short. Long-running operations, such as file or
network access, should run in a `RunnableNode` so the window remains
responsive.

The pool has up to two workers and a bounded queue. A task must not wait for
another task in the same pool to start before it can finish. Use `onClose` to
release resources or handle task completion. Interruption is cooperative, so
long-running tasks should check `Thread.currentThread().isInterrupted()` or
handle `InterruptedException`.

## Change listeners

For components that use `ChangeListener`, use `ChangeNode`. Its callback
receives the `ChangeEvent` and runs on the EDT:

```java
import javax.swing.event.ChangeListener;

ChangeListener listener = new ChangeNode(event -> {
    sliderLabel.setText("Value changed");
});

slider.getModel().addChangeListener(listener);
```

## Shut down the application

When the main window closes, the application calls
`ThreadMaster.shutdown()`. This interrupts running tasks and invokes their
`onClose` callbacks. A shut-down manager cannot be initialized again.
