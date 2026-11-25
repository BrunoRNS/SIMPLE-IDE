# Terminal API

The terminal is a Swing panel backed by JediTerm and a bundled BusyBox process.
`io.simpleide.api.Terminals` creates the panel and its lifecycle controller.

## Supported platforms

| Platform | Binary resource |
| --- | --- |
| Linux x86 | `linux/linux-x86/bin/busybox` |
| Linux x64 | `linux/linux-x64/bin/busybox` |
| Linux ARM64 | `linux/linux-arm64/bin/busybox` |
| macOS Intel | `macos/macos-intel/bin/busybox` |
| macOS Apple Silicon | `macos/macos-arm64/bin/busybox` |
| Windows x86 | `windows/windows-x86/bin/busybox.exe` |
| Windows x64 Unicode | `windows/windows-x64-unicode/bin/busybox.exe` |
| Windows ARM64 | `windows/windows-arm64/bin/busybox.exe` |

On 64-bit Windows versions older than Windows 10, Simple IDE falls back to the
x86 binary. `Terminals.isSupported()` returns `false` on unknown platforms.

All Linux binaries are linked against GNU glibc and require glibc 2.28 or newer.
Alpine Linux and other musl-based distributions are not supported. See
[Linux compatibility](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Reference-Linux) for the full distribution list.

## Create a terminal

```java
import io.simpleide.api.Terminals;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;
import javax.swing.JFrame;

TerminalPanel panel = Terminals.panel();
BusyBoxTerminalController controller = Terminals.controller(panel);

JFrame frame = new JFrame("Terminal");
frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
frame.add(panel);
frame.setSize(900, 500);
frame.setVisible(true);

frame.addWindowListener(
    new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent event) {
        controller.onWindowClosing(frame::dispose);
      }
    });
```

`Terminals.controller(panel)`:

- Initializes `ThreadMaster`.
- Creates a dedicated terminal worker pool named `terminal`.
- Wires the add and close buttons.
- Enables at most five tabs (`TerminalPanel.MAX_TABS`).

## Initial working directory

Each terminal session selects its working directory when it starts. In Simple IDE,
an open Explorer folder takes priority. If no folder is open, the terminal starts in
the user's home directory when it is accessible; otherwise it falls back to the
application startup directory (`user.dir`). If neither directory is accessible,
session startup fails with an explicit error.

The directory is resolved separately for each new session, so opening or closing an
Explorer folder affects subsequently created terminals without changing existing
sessions. Standalone controllers created with `Terminals.controller(panel)` use the
home/startup-directory fallback. Hosts that need a custom directory can provide a
`Supplier<Path>` to `BusyBoxTerminalController`.

## How a session starts

1. The user presses Add.
2. The controller assigns a session id and creates a tab.
3. The controller resolves the initial working directory.
4. A terminal task extracts the platform BusyBox binary to a temporary file.
5. On non-Windows platforms the file is marked executable.
6. The process starts in a pseudo-terminal through pty4j in the selected directory.
7. The tab is shown with a JediTerm view connected to the process.
8. The temporary binary is deleted when the executable object closes.

No system-wide BusyBox installation is required.

## Lifecycle

The controller exposes one public lifecycle method:

```java
controller.onWindowClosing(() -> frame.dispose());
```

Call it from the window's close handler. It:

- Marks the controller as closing.
- Prevents new tabs.
- Requests close on every open tab.
- Schedules graceful close for sessions that finished launching.
- Calls the supplied callback when all tabs are closed.

Terminal tasks run on the dedicated `terminal` pool. Closing does not block the
EDT.

## Supported behavior

- Multiple tabs, up to five.
- Add and close buttons.
- Terminal output and input through JediTerm.
- UTF-8 input, including dead-key and composed characters such as `~`, `´`, `á`,
  `ã` and `ç`. The connector always writes UTF-8 to the PTY.
- Mouse text selection. Drag with the primary button to select, then copy or paste.
- Copy with `Ctrl+Shift+C` and paste with `Ctrl+Shift+V`. macOS uses `Cmd+C` and
  `Cmd+V`. Plain `Ctrl+C` remains the terminal interrupt signal.
- Platform-specific BusyBox shell.
- Graceful termination followed by forced termination if needed.
- Process-exit diagnostics in an error popup when a session fails unexpectedly.

On macOS, Option acts as a compose key instead of sending a Meta escape prefix, so
layouts that type accents and dead keys with Option work in the terminal. Use the
terminal shortcuts above rather than Option-based Meta bindings.

## Limitations

- The panel has no public API for creating a tab programmatically; the add
  button is the supported entry point. A host application can drive the button
  through its action listener only if it is granted access; this is intentionally
  not part of the public API.
- The tab title is the session id, not the current working directory.
- The terminal is not a full terminal emulator; it depends on JediTerm's
  supported escape sequences.
- There is no built-in SSH, split panes or session persistence.
- BusyBox is GPLv2 software. Keep the bundled source archive and license when
  redistributing.

## BusyBox customization

The binaries and source archives live under
`simpleide/src/main/resources/io/simpleide/ui/terminal/core/busybox/`. Each
platform directory contains:

- `bin/busybox` or `bin/busybox.exe`.
- A source archive named `*src.tar.bz2`.
- `LICENSE.txt`.
- `README.txt` with version, format, provenance and build notes.

`BusyBoxPlatform` maps `LangPlatform` to the resource path and extracts the
binary. See [BusyBox third-party notes](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Third-Party-BusyBox) before
replacing a binary.

## Security

- The extracted binary lives in a temporary directory created by the JVM.
- It is deleted when the session ends.
- The terminal runs with the same user permissions as Simple IDE.
- Do not run untrusted commands in the embedded terminal unless the host
  application sandboxes the process.
- On macOS the binary is not signed or notarized; Gatekeeper may affect a
  packaged app that launches it. See [Build and packaging](https://github.com/BrunoRNS/SIMPLE-IDE/wiki/Build-Makefile).

## Troubleshooting

| Symptom | Cause | Action |
| --- | --- | --- |
| Add button disabled | Unsupported platform or five tabs open | Check `Terminals.isSupported()` and close a tab |
| "process exited immediately" | Binary cannot run (architecture, quarantine, permissions) | Check the popup diagnostics and the platform resource |
| Terminal starts but shows no prompt | BusyBox startup failure | Run the extracted binary manually and inspect stderr |
| macOS blocks the binary | Quarantine/signing policy | Sign/notarize the packaged app or allow the binary |
| Window close hangs | A session ignores graceful close | The controller escalates to force termination after a timeout |
