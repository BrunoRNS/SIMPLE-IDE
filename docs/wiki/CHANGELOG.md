# Changelog

All notable changes to this project are documented here. This changelog follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html), and lists the newest
changes first. Version 1.1.0 is still in development, so its changes remain under
`Unreleased` until that release is published.

## [Unreleased]

### Added

- Optional audio and video playback in read-only editor tabs through JavaFX, with a
  platform-neutral `MediaPlayback` API and a hex-editor fallback. Maven consumers choose
  `nojavafx`, `javafx22` or `javafx23` with `-Dsimpleide.media.variant`; without the
  property the variant is derived from the consumer JDK and OS/architecture. Linux
  `arm64` always uses `nojavafx`. See
  [JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media).
- `make test-consumer` and `scripts/ci/test-consumer-profiles.py` verify the consumer
  variant contract for automatic selection and the three forced variants on every
  supported OS/architecture.
- Restored legacy helpers and the language icon resources used by older integrations.
  These are compatibility-only; new code should use the current APIs and Material
  Icon Theme.

The eight per-language PNG icons restored in
`simpleide/src/main/resources/io/simpleide/icon/` are `assembly.png`, `c.png`,
`cpp.png`, `groovy.png`, `java.png`, `javascript.png`, `kotlin.png` and
`python.png`. They are retained for legacy callers; the default tree renderer in
v1.1.0 uses `io.simpleide.ui.icon.MaterialIcons`.

### Changed

- `TreeFileCellRenderer` now resolves icons through `MaterialIcons` instead of the
  per-language `TypeFile` map. The rendered icons differ from v1.0.0beta even though
  the class and its public API are unchanged.
- `MainFrame` opens projects through `ExplorerPanel` and open-folder actions, and can
  now close the open folder. The old private loading methods remain only as deprecated
  compatibility code.
- `LogPanel` rebuilds its document from stored entries on theme changes instead of
  patching character attributes in place.

**Breaking changes from v1.0.0beta:** The following changes affect source
compatibility (callers no longer compile) and binary compatibility
(`NoSuchMethodError` for already compiled callers).

| Broken API (v1.0.0beta) | What breaks | Migration |
| --- | --- | --- |
| `FileMenu(Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile)` | The four-argument constructor no longer exists; the “Set directory” menu item was removed. | Use `new FileMenu(createFile, createFolder, openFolder, closeFolder, saveFile)`. Map the former `setDirectory` action to `openFolder`; `closeFolder` is new. |
| `IdeMenuBar(Runnable createFile, Runnable createFolder, Runnable setDirectory, Runnable saveFile)` | The four-argument constructor no longer exists. | Use the five-argument constructor with `openFolder` and `closeFolder`. |
| `IdeMenuBar.createItems(Runnable, Runnable, Runnable, Runnable)` (private) | Reflection-based callers can no longer find this method; normal callers are unaffected. | Use the five-argument `createItems`; there is no public replacement. |
| `MenuBarFactory.createMenuBar(JFrame, Runnable, Runnable, Runnable, Runnable)` | The five-argument method no longer exists. | Use `createMenuBar(frame, createFile, createFolder, openFolder, closeFolder, saveFile)`. |
| `LspAutoCompletion(Editor, LspCompletionProvider)` | Previously compiled callers linked to the old constructor descriptor throw `NoSuchMethodError`. Source calls still compile because `LspCompletionProvider` implements the broader `CompletionProvider` type. | Recompile against v1.1.0; no source change is required when the argument is an `LspCompletionProvider`. |
| `ButtonExit.TabIcon(boolean)` (private) | Reflection-based callers can no longer find the old constructor; the icon now also receives a size. | Use the two-argument constructor; there is no public replacement. |
| `EditorWelcomeScreen.createButton(String)` (private) | Reflection-based callers can no longer find the old method; it now also receives a `primary` flag. | Use `createButton(String, boolean)`; there is no public replacement. |

### Deprecated

The following members existed in v1.0.0beta, were removed in v1.1.0 and are now
restored with their original behavior. They are marked `@Deprecated` in the
source and are intended only for legacy systems. New code should use the
replacement shown below.

| Class | Restored member | Use instead |
| --- | --- | --- |
| `io.simpleide.frame.MainFrame` | `private void loadTree(JScrollPane)` | `MainFrame.loadFolder(Path)` / `MainFrame.openFolder()` |
| `io.simpleide.frame.MainFrame` | `private void setDirectory()` | `MainFrame.openFolder()` |
| `io.simpleide.frame.log.LogPanel` | `private void applyDocumentBackground(Color)` | `LogPanel.rebuildDocument()` with the themed look and feel |
| `io.simpleide.ui.menubar.ThemeManager` | `private void applyReadableSyntaxColors(Theme, ThemeType)` | `ThemeManager.applySyntaxPalette(Theme, SyntaxPalette)` |
| `io.simpleide.ui.menubar.ThemeManager` | `private void applyDistinctSyntaxColors(Theme, ThemeType)` | `ThemeManager.applySyntaxPalette(Theme, SyntaxPalette)` |
| `io.simpleide.ui.menubar.ThemeManager` | `private void setForeground(Theme, Color, int...)` | `ThemeManager.setSyntaxForeground(Theme, Color, int...)` |
| `io.simpleide.ui.terminal.TerminalPanel` | `static Color getAccessibleIconForeground(Color, Color)` | `ThemeColors.readable(preferred, background, ThemeColors.MIN_LARGE_TEXT_CONTRAST)` |
| `io.simpleide.ui.terminal.TerminalPanel` | `private static double contrastRatio(Color, Color)` | `ThemeColors.contrastRatio(Color, Color)` |
| `io.simpleide.ui.terminal.TerminalPanel` | `private static Color composite(Color, Color)` | `ThemeColors.blend(Color, Color, float)` (explicit ratio instead of alpha) |
| `io.simpleide.ui.terminal.TerminalPanel` | `private static double relativeLuminance(Color)` | `ThemeColors.relativeLuminance(Color)` |
| `io.simpleide.ui.terminal.TerminalPanel` | `private static double linearize(int)` | Internal `ThemeColors` luminance helpers |
| `io.simpleide.ui.treefile.TreeFileCellRenderer` | `private static Icon loadIcon(String)` | `MaterialIcons.forFile(Path)` / `MaterialIcons.forDirectory(Path, boolean)` |
| `io.simpleide.ui.treefile.TreeFileCellRenderer` | `private static Map<TypeFile, Icon> loadFileIcons()` | `MaterialIcons.forFile(Path)` |

The deprecated helpers reproduce the v1.0.0beta behavior, including the private
`TerminalPanel.MIN_ICON_CONTRAST` constant used by `getAccessibleIconForeground`.
`TreeFileCellRenderer.loadIcon` and `loadFileIcons` still read the old PNG files,
which is why those resources remain in the repository. `MainFrame.loadTree` keeps
the old loading flow but opens `SimpleIDEFile` entries because
`EditorManager.openFile` in v1.1.0 accepts that type. The restored members are
not called by the IDE itself.

Deprecated compatibility members will not receive new features and may be
removed in a future major release. New integrations should use the replacements
listed above and the Material Icon Theme.

[Unreleased]: https://github.com/BrunoRNS/SIMPLE-IDE/compare/v1.0.0beta...HEAD
