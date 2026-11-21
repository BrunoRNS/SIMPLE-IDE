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
| `ThemeMenu` | Light, Dark, Dracula, Dracula Light, Brown and Brown Light radio items |

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
| `DRACULA_LIGHT` | `dracula-light.properties` | `default.xml` with the light Dracula palette |
| `BROWN` | `brown.properties` | `dark.xml` with brown accents |
| `BROWN_LIGHT` | `brown-light.properties` | `dark.xml` with light brown accents |

`register(Window)` applies the current theme to a window. `register(Editor)` keeps
the editor in a list so later theme changes apply to it. Call `unregister(Editor)`
before discarding an editor that will not be reused.

Theme changes run on the EDT through `ThreadMaster`. The manager reads bundled
resources and is not headless-safe because it updates open windows.

When embedding or launching the application from the fat JAR, keep the bundled
resources on the runtime classpath. `ThemeManager` loads FlatLaf properties,
RSyntaxTextArea XML themes, theme palettes and welcome screen SVG logos from
those resources. The application icon is bundled at
`/io/simpleide/icon/simpleide_icon.png`. `SimpleIDE.launch()` starts the complete
application with these resources; custom Swing applications can use the manager
directly as shown above.

The welcome screen logo has a separate SVG for each theme. Its canvas, primary
mark and accent use the selected palette, and its detail fills use the theme
foreground so small shapes remain visible on light and dark backgrounds. The
canvas and inner counters of the P and D are transparent, so they reveal the
welcome page's actual background color. The logo scales proportionally into the
space between the welcome text and its action buttons.

## Testing

The menus and theme manager are covered by `FileMenuTest`, `HelpMenuTest`,
`ThemeMenuTest` and `ThemeManagerTest` under
`simpleide/src/test/java/io/simpleide/ui/menubar`.
