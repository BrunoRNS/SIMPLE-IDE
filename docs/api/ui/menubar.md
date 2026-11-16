# Menu bar and themes

`io.simpleide.ui.menubar` builds the application menu bar and applies themes.
`io.simpleide.ui.menubar.menus` contains the individual menus.

## Building the menu bar

`MenuBarFactory.createMenuBar(frame, createFile, createFolder, setDirectory,
saveFile)` installs an `IdeMenuBar` on the frame. Each callback is a `Runnable`
supplied by the frame.

```java
import io.simpleide.ui.menubar.MenuBarFactory;

MenuBarFactory.createMenuBar(
    frame,
    () -> tree.createNewFile(),
    () -> tree.createNewDirectory(),
    this::chooseDirectory,
    editorManager::saveSelectedFile);
```

| Menu | Items |
| --- | --- |
| `FileMenu` | New File, Save (Ctrl+S), New Folder, Set directory |
| `SettingsMenu` | Theme submenu and a global font size spinner |
| `HelpMenu` | Keyboard Shortcuts, Machine Information, About Simple IDE |
| `ThemeMenu` | Light, Dark, Darcula, Brown, Brown Light radio items |

File and theme menu items dispatch through `ActionNode`, so the callbacks are
tracked EDT nodes. The font size spinner uses `ChangeNode` and rewrites every
`FontUIResource` in the UI defaults before refreshing all open windows.

## Themes

`ThemeManager` is the singleton behind `ThemeMenu`. It applies a FlatLaf look and
feel and an RSyntaxTextArea theme.

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

| Value | Look and feel | Editor theme |
| --- | --- | --- |
| `LIGHT` | `FlatLightLaf` | `default.xml` with readable syntax overrides |
| `DARK` | `FlatDarkLaf` | `dark.xml` with readable syntax overrides |
| `DRACULA` | `dracula.properties` | `dracula.xml` with the Dracula palette |
| `BROWN` | `brown.properties` | `dark.xml` with brown accents |
| `BROWN_LIGHT` | `brown-light.properties` | `dark.xml` with light brown accents |

`register(Window)` applies the current theme to a window. `register(Editor)` keeps
the editor in a list so later theme changes apply to it. Call `unregister(Editor)`
before discarding an editor that will not be reused.

Theme changes run on the EDT through `ThreadMaster`. The manager reads bundled
resources and is not headless-safe because it updates open windows.

## Testing

The menus and theme manager are covered by `FileMenuTest`, `HelpMenuTest`,
`ThemeMenuTest` and `ThemeManagerTest` under
`simpleide/src/test/java/io/simpleide/ui/menubar`.
