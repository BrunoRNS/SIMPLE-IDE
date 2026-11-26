# Changelog

All notable changes to this project are documented here. This changelog follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html), and lists the newest
changes first. Version 1.1.0 records the release state. Later work belongs under
`Unreleased / Next versions`.

## v1.1.0 (released)

### Added

- Run I/O tab with standard input/output and stop controls, an Execute menu for
  run/debug actions, and DAP integration. See [Run I/O and debugging](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Features-Io-tab).
- Git Source Control side panel with repository status, branch operations and
  commit history. See [Git side panel](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Features-Git-sidepanel).
- Optional audio and video playback in read-only editor tabs through JavaFX, with a
  platform-neutral `MediaPlayback` API and a hex-editor fallback. Consumers can pin
  `nojavafx`, `javafx22` or `javafx23` directly in `pom.xml` with explicit OpenJFX
  dependencies; the advanced `-Dsimpleide.media.variant` property and automatic
  JDK/OS selection remain available. Linux `arm64` uses `nojavafx` automatically
  and supports released JavaFX variants on glibc 2.36+ (Debian 12+). Audio uses the JavaFX
  stack only; JSE-SPI and its dependencies are absent. See
  [JavaFX media variants](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-JavaFX-Media) and
  [audio/video viewers](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Features-Mediaviewer).
- Release packages and fat JARs for every supported OS/architecture/variant
  combination. Linux JavaFX `arm64` builds use Debian 12. Download-only helper
  scripts are available for
  Linux, macOS and Windows. See
  [Script installation](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Install).
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

### Fixed

- The terminal accepts dead-key and accented characters. On macOS, Option now composes
  `~`, `´`, `á`, `ã` and similar characters instead of sending a Meta escape prefix
  that leaked an extra character and dropped the composed one.
- Terminal text selection and `Ctrl+Shift+C`/`Ctrl+Shift+V` copy and paste are covered
  by integration tests, plus UTF-8 input through the PTY.
- Stopping a running language program no longer reports a bogus process startup
  failure when its output stream closes during shutdown.
- Source Control Git operations no longer freeze the window. Every Git command runs
  on the `ThreadMaster` background pool and returns to the EDT only to update the
  panel or show an error popup.
- Git commands are forced to be non-interactive: standard input is closed, terminal
  credential prompts, askpass, pagers, editors and SSH passphrase prompts are
  disabled. A command that still requests input stops and displays an error popup
  with credential setup guidance instead of waiting for the user.

## Unreleased / Next versions

<!-- Add entries here only after they are explicitly assigned to a later release. -->

[v1.1.0]: https://github.com/BrunoRNS/SIMPLE-IDE/compare/v1.0.0beta...v1.1.0
[Unreleased]: https://github.com/BrunoRNS/SIMPLE-IDE/compare/v1.1.0...HEAD
