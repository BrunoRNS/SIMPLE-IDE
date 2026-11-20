# Popups

`io.simpleide.frame.popup` contains the modal dialogs used by the application and
by reusable components such as the editor and terminal. Every popup wraps a
`JOptionPane` and is displayed through the thread manager.

## Components

| Class | Purpose |
| --- | --- |
| `PopUP` | Abstract base: option pane, visibility flag and lifecycle |
| `InfoPopUP` | Informational message |
| `SuccessPopUP` | Success message |
| `WarningPopUP` | Warning message with a configurable option |
| `ErrorPopUP` | Error message plus an `ErrorTypePopUp` category |
| `InputPopUP` | Single-line text input with a `Consumer<String>` callback |
| `ErrorTypePopUp` | Enum of error categories used as the error dialog title |

## Showing a dialog

Every concrete popup offers a convenience constructor and `run()`. `run()` is a
shortcut for `startManaged()`.

```java
import io.simpleide.frame.popup.InfoPopUP;

new InfoPopUP("Build", "The build finished successfully.").run();
```

`startManaged()` registers the dialog as a thread tree node and returns a
`NodePattern`:

```java
import io.simpleide.frame.popup.ErrorPopUP;
import io.simpleide.frame.popup.utils.ErrorTypePopUp;
import io.simpleide.thread.nodes.NodePattern;

NodePattern node = new ErrorPopUP(
        "Unable to save the file.", parent, ErrorTypePopUp.INTERNAL_ERROR)
    .startManaged();
```

Only one instance of a given popup may be visible at a time. Calling
`startManaged()` on a visible popup throws `IllegalStateException`; a missing
option pane throws `NullPointerException`.

## Lifecycle

- `run()` and `startManaged()` queue the dialog on the EDT through
  `ThreadMaster.executeOnEDT`.
- The popup is marked visible before the EDT task is queued, and the flag is reset
  when the dialog closes, including when the close is caused by an exception.
- `onDialogClosed()` is the hook subclasses use to read the selected option.
  `ForceCloseConfirmationPopUP` in the terminal package is a good example: it
  reports `true` for "Force exit" and `false` for "Cancel".
- `getMap()` and `setMap(Map)` expose the popup data as a string-keyed map for
  tooling and diagnostics. `toString()` renders the same data.

## Threading

Call the popups from any thread; they marshal themselves to the EDT. Do not block
the calling thread waiting for a modal result — use the callback in
`InputPopUP` or override `onDialogClosed()`.

## Testing

Popup behavior is covered by `ErrorPopUPTest`, `InfoPopUPTest`,
`InputPopUPTest`, `SuccessPopUPTest`, `WarningPopUPTest` and
`ErrorTypePopUpTest` under `simpleide/src/test/java/io/simpleide/frame/popup`.
