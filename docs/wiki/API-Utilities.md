# Shared utilities

`io.simpleide.utils` contains small helpers used across the application. The
`mappeable` and `service` subpackages add reflective map conversion and file
operations for the project tree.

## File, resource and path helpers

| Class | Responsibility |
| --- | --- |
| `FileUtil` | Reads strict UTF-8 text from regular files |
| `ResourceUtil` | Resolves classpath resources as filesystem paths |
| `OSpathUtil` | Locates executables on the host `PATH` |

`ResourceUtil` works both from a packaged JAR and from a source checkout. It is
used by the editor, the tree renderer and the theme manager to load icons and
theme files.

`OSpathUtil.which(name)` returns the resolved `Path` of an executable or `null`
when it is not on the path. It is the basis for toolchain discovery in
`io.simpleide.language`.

```java
import io.simpleide.utils.FileUtil;
import io.simpleide.utils.OSpathUtil;
import java.nio.file.Path;

String source = FileUtil.readString(Path.of("Example.java"));
Path python = OSPathUtil.which("python3");   // null when unavailable
```

`FileUtil` rejects directories and invalid UTF-8. Callers that need a different
charset should read the file themselves.

## Map conversion

`io.simpleide.utils.mappeable` converts annotated objects to and from string-keyed
maps:

- `@Mappeable` marks a type as eligible for conversion.
- `MappeableSupport` provides default `getMap()` and `setMap(Map)` implementations
  that read and write the non-static fields of the implementing class and its
  superclasses.

```java
import io.simpleide.utils.mappeable.Mappeable;
import io.simpleide.utils.mappeable.MappeableSupport;
import java.util.Map;

@Mappeable
final class Settings extends MappeableSupport {
  private String theme = "dark";
  private int fontSize = 12;
}

Settings settings = new Settings();
Map<String, Object> values = settings.getMap();
settings.setMap(values);
```

The produced map is a snapshot: changing the map does not change the object until
`setMap` is called. Values are exposed without copying, so treat mutable values
inside the map as read-only. Conversion is reflective and intended for simple
configuration or diagnostic data, not for persistence of arbitrary object graphs.

## File operations

`io.simpleide.utils.service.FileService` backs the tree context menu and the editor
save path:

| Method | Behavior |
| --- | --- |
| `saveFile(Path, String)` | Writes UTF-8 text, replacing the file contents |
| `deleteFile(Path)` | Deletes a file or directory tree without following symbolic links |
| `renameFile(Path, String)` | Moves an entry within its parent directory |
| `createNewFile(Directory, String, boolean)` | Creates a file or directory and returns its node |

Names are validated to a single relative path segment. Absolute paths, `.`, `..`
and multi-segment names throw `IllegalArgumentException`, which prevents a caller
from escaping the selected parent directory.

## Icon loading

`io.simpleide.frame.utils.LoadIcon` loads icon resources from the classpath and
wraps a missing resource in `MissingResourceException`. Prefer `ResourceUtil` when
you need a `Path`, and `LoadIcon` when you need an `Icon` for a Swing component.

## Testing

Coverage lives in `ResourceAndFileUtilTest` and `FileServiceTest` under
`simpleide/src/test/java/io/simpleide/utils`.
