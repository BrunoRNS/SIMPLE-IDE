# UI helpers

The `io.simpleide.ui` and `io.simpleide.frame.popup` packages contain the Swing
helpers used by the desktop application. Most are intended for the application
itself, but a few are useful when embedding Simple IDE components.

## Themes

`io.simpleide.ui.menubar.ThemeManager` applies a FlatLaf look and feel and an
RSyntaxTextArea theme.

```java
import io.simpleide.api.Threads;
import io.simpleide.ui.menubar.ThemeManager;
import io.simpleide.ui.menubar.ThemeManager.ThemeType;

Threads.master();
ThemeManager themes = ThemeManager.getInstance();
themes.register(frame);
themes.register(editor);
themes.setTheme(ThemeType.DRACULA);
```

Available themes:

| Value | Look and feel | Editor colors |
| --- | --- | --- |
| `LIGHT` | FlatLightLaf | `default.xml` plus readable syntax overrides |
| `DARK` | FlatDarkLaf | `dark.xml` plus readable syntax overrides |
| `DRACULA` | Flat properties | `dracula.xml` with Dracula palette |
| `BROWN` | Flat properties | dark palette with brown accents |
| `BROWN_LIGHT` | Flat properties | light palette with brown accents |

`register(Window)` applies the current theme to a window. `register(Editor)`
tracks an editor so later theme changes apply to it. Call
`unregister(Editor)` before discarding an editor that will not be reused.

Theme changes run on the EDT through `ThreadMaster`. The manager is a singleton
and reads bundled resources. It is not headless-safe because it updates windows.

## Popups

The popup classes wrap modal dialogs:

| Class | Purpose |
| --- | --- |
| `InfoPopUP` | Informational message |
| `SuccessPopUP` | Success message |
| `WarningPopUP` | Warning message |
| `ErrorPopUP` | Error message with an error type |
| `InputPopUP` | Single-line text input with a callback |
| `PopUP` | Base class for custom popups |

```java
import io.simpleide.frame.popup.InfoPopUP;

new InfoPopUP("Build", "The build finished successfully.").run();
```

Most constructors accept a parent `JComponent` and an option object, and provide
a convenience constructor without them. `run()` displays the dialog on the EDT.
`startManaged()` returns a `NodePattern` if you want to integrate the dialog with
the thread tree.

`InputPopUP` takes a `Consumer<String>`:

```java
import io.simpleide.frame.popup.InputPopUP;

new InputPopUP("Name", "Enter a name", parent, value -> System.out.println(value)).run();
```

## File tree

The `io.simpleide.ui.treefile` package contains the project tree model:

| Class | Purpose |
| --- | --- |
| `Tree` | Swing tree component |
| `TreeFile` | Base node |
| `Directory` | Directory node |
| `File` | File node and `typeOf(Path)` |
| `TypeFile` | File type enum |
| `Watcher` | Directory change watcher |
| `TreeState` | Expansion/selection state |

These classes are coupled to the desktop application's file actions and editor
manager. They are not a stable standalone API. Use them only if you are
extending the application itself.

## Editor actions

`io.simpleide.ui.editor.EditorActions` wires editor menu and key actions,
including LSP definition navigation. It expects an `EditorManager` and the
application's tab model. For an embedded editor, call `EditorTab` methods
directly instead.

## Icons and resources

`io.simpleide.utils.ResourceUtil` loads resources from the classpath or from the
filesystem when running from a source checkout. The terminal panel uses FlatLaf
SVG icons and adjusts their color for contrast. If you reuse the terminal panel
with a custom theme, verify the icon contrast with your background colors.

## Headless environments

Do not create Swing components in a headless JVM. The editor, terminal, popups
and theme manager all create or manipulate Swing objects. The threading and
language APIs can run headless, as long as the language toolchains themselves do
not require a display.

## Threading rules

- Create and mutate Swing components on the EDT.
- `ThemeManager` schedules its own updates on the EDT.
- Popups call `run()` through the Swing dialog mechanism; call them from the EDT.
- Background work that updates a component should wrap the update in
  `Threads.onEdt(...)`.

## Stability

The public facade `io.simpleide.api` is the intended integration surface. Classes
under `io.simpleide.ui.*` are public because the application modules need them,
but their method signatures may change between versions. Prefer the facade and
the editor/terminal classes documented in this API section.
