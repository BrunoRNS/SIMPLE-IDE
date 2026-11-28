# Project file tree

`io.simpleide.ui.treefile` is the model and Swing component behind the project
explorer. It is part of the desktop application; the types are public because the
frame and utilities use them, but they are not a stable standalone API.

## Model

| Class | Responsibility |
| --- | --- |
| `TreeFile` | Abstract entry with a path and display name |
| `Directory` | Directory entry with lazily loaded children |
| `SimpleIDEFile` | Source file with its `TypeFile`, content kind and in-memory text buffer |
| `TypeFile` | Extension classification used for highlighting, icons and language ids |
| `TreeState` | Selected entry and current directory |

`SimpleIDEFile.typeOf(Path)` maps an extension to a `TypeFile`. Known source
extensions map to Java, C#, Kotlin, Groovy, JavaScript, Go, Rust, Python, C and
C++; anything else maps to `TXT`. Files that cannot be decoded as UTF-8 become
`BINARY` entries with empty text content. Image and binary data are kept separate
from the mutable text buffer, and files discovered in the tree are loaded lazily
when opened.

```java
import io.simpleide.ui.treefile.SimpleIDEFile;
import io.simpleide.ui.treefile.TypeFile;

TypeFile type = SimpleIDEFile.typeOf(Path.of("Example.java"));   // TypeFile.JAVA
```

`Directory` loads children when constructed with `loadImmediately = true`, which
is what `new Directory(path)` does. Children created from the tree expansions are
loaded lazily. `refreshDirectory` re-reads a directory from disk and replaces its
children. File content metadata is published together as an immutable snapshot so
readers observe one consistent classification and content state.

## Swing component

`Tree` extends `JTree` and installs `TreeFileCellRenderer` and the context menu
from `TreeFileActions`.

```java
import io.simpleide.ui.treefile.Tree;
import io.simpleide.ui.treefile.TreeFileFactory;

Tree tree = TreeFileFactory.createDirectory();          // user.dir
Tree other = TreeFileFactory.createDirectory(path);     // explicit root
```

| Member | Purpose |
| --- | --- |
| `getState()` | Selection and current directory |
| `setRoot(Directory)` | Replace the root, marshalled to the EDT |
| `setRootChangeListener(Consumer<Directory>)` | Observe root changes |
| `refreshDirectory(Path)` | Re-read a directory from disk |
| `createNewFile()` / `createNewDirectory()` | Run the context-menu creation actions |

`TreeFileActions` adds New file, New folder, Rename and Delete. Each action
validates the name, performs the operation through `FileService` and updates the
tree model. Failures open an `ErrorPopUP`.

## Watching the file system

`Watcher` registers every directory under a root with a `WatchService` and consumes
create and delete events on a managed background node. Root changes are detected
by polling every 250 ms and applied on the EDT. The watcher is started by
`MainFrame` and closed when the window closes.

```java
import io.simpleide.ui.treefile.Watcher;

try (Watcher watcher = new Watcher(tree)) {
  watcher.start(rootPath);
  // ...
}
```

## Icons

`TreeFileCellRenderer` uses different icons for open and closed folders and one
icon per supported language. Types without an icon keep the default renderer
icon.

## Testing

The tree model and watcher are covered by `TreeFileTest` and `WatcherTest` under
`simpleide/src/test/java/io/simpleide/ui/treefile`. `FileService` has its own
`FileServiceTest`, and `ResourceAndFileUtilTest` covers the shared file and
resource helpers.
