# Application bootstrap and frame

This page documents `io.simpleide`, `io.simpleide.frame` and
`io.simpleide.frame.layout`. These classes make up the desktop application around
the reusable components. Embedders normally use `io.simpleide.api.SimpleIDE` or
the individual facades instead of instantiating the frame directly.

## Bootstrap

`io.simpleide.App` is the `main` entry point of the executable JAR:

1. It installs every language whose toolchain is available by running
   `LangSetup` against the shared `LangConfigHolder.LANG_CONFIG`.
2. It registers an `EDTNode` that creates `MainFrame`.
3. It calls `ThreadMaster.getInstance().initialize()`, which starts the thread
   tree and runs the registered frame node on the Swing Event Dispatch Thread.

`io.simpleide.SimpleIDEVersion.VERSION` exposes the version read from
`/simpleide.properties`. `io.simpleide.api.SimpleIDE.version()` is the facade for
the same value.

```java
import io.simpleide.api.SimpleIDE;

SimpleIDE.launch(args);   // equivalent to io.simpleide.App.main(args)
```

Do not call `App.main` from a process that already owns a `MainFrame`; the frame
registers log appenders and cannot be constructed twice in the same JVM.

## Main frame wiring

`io.simpleide.frame.MainFrame` extends `JFrame` and implements `Runnable`. Its
`run()` method must execute on the EDT. It creates and connects:

| Component | Role |
| --- | --- |
| `Tree` and `Watcher` | Project tree, loaded on a background node, refreshed on disk changes |
| `EditorManager` | Editor tabs and language server connections |
| `TerminalPanel` + `BusyBoxTerminalController` | Embedded terminal in the output tabs |
| `LogPanel` + `SwingLogAppender` | Debug console attached to the Logback root logger |
| `LspStatusBar` | Language server status line |
| `MenuBarFactory` | File, Settings and Help menus |
| `ThemeManager` | Look and feel and editor colors |

The window uses `DO_NOTHING_ON_CLOSE` and performs an orderly shutdown in
`windowClosing`: it saves every modified editor file first, then asks the terminal
controller to close every session. If any file cannot be saved, closing is
cancelled and the error is written to the application log. On `windowClosed` it
closes the watcher and editor manager, shuts down `ThreadMaster`, and removes
the tray registration last. The tray entry stays active while unsaved-file checks
or terminal-session shutdown are in progress.

The bundled `simpleide_icon.png` is decoded from the classpath and applied to
the window and supported taskbar/dock APIs. Runtime variants from 16 to 256
pixels are supplied to window managers. The same icon is registered in the tray
until application shutdown: Linux first uses the desktop's StatusNotifierItem
service (including Wayland hosts such as Waybar), then falls back to Java's
`SystemTray`; Windows and macOS use `SystemTray`. Clicking the tray entry
restores the window. Tray or dock integration is skipped only when the desktop
session does not provide the corresponding host API.

The jpackage scripts also pass `--icon` explicitly: Linux uses the 256-pixel
PNG, Windows uses a multi-size ICO, and macOS uses an ICNS bundle. All variants
are resized proportionally from `simpleide_icon.png`.

The initial window size is capped at 1440 × 810 and fitted to the usable screen
bounds, with a 16:9 content ratio where possible. Split pane positions are set
after the window becomes visible so they use the final layout dimensions.

`MainFrame.getEditor()` returns the editor selected in the application editor
manager, or `null` when no manager or tab exists.

## Layout helpers

`io.simpleide.frame.layout.MainLayout` extends `GridBagLayout` and adds
`addComponent(component, x, y, width, height)` plus weighted overloads. It keeps
the grid-bag constraints boilerplate out of the frame code.

`io.simpleide.frame.layout.SimpleLayout` extends `FlowLayout` and adds helpers for
left-, center- and right-aligned components.

Both helpers are application-internal. They are not a stable embedding API; for a
custom window, use standard Swing layout managers.

## Threading and lifecycle

- Create the frame on the EDT. `App` does this through an `EDTNode`.
- Load the project tree and directory changes on background nodes, then apply
  them to Swing components through `ThreadMaster.executeOnEDT`.
- Closing the frame stops the whole application. Do not use `MainFrame` inside a
  larger application unless you also own `ThreadMaster`.

## Related pages

- [Threading](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-Threading) — the node model used by the frame.
- [UI helpers](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI) — themes, popups and icons.
- [File tree](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/API-UI-File-Tree) — the tree model shown by the frame.
