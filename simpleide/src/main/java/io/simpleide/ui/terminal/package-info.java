/**
 * Embedded terminal backed by JediTerm and a bundled BusyBox shell.
 *
 * <p>
 * {@link io.simpleide.ui.terminal.TerminalPanel} hosts up to five tabbed terminal views
 * and the add/close actions. {@link io.simpleide.ui.terminal.BusyBoxTerminalController}
 * owns the session lifecycle: it extracts the platform BusyBox binary, launches one PTY
 * process per tab, coordinates shutdown and reports failures.
 * {@link io.simpleide.ui.terminal.BusyBoxTerminalSession} performs the process and PTY
 * work, {@link io.simpleide.ui.terminal.TerminalView} renders a session and
 * {@link io.simpleide.ui.terminal.TerminalSettingsProvider} derives terminal colors and
 * font from the active Swing theme.
 *
 * <p>
 * {@link io.simpleide.ui.terminal.ProcessTreeTerminator} terminates the shell and its
 * captured descendants safely, and
 * {@link io.simpleide.ui.terminal.ForceCloseConfirmationPopUP} asks before forcing a
 * running process to exit.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/terminal.md">docs/api/terminal.md</a>
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/third-
 * party/BUSYBOX.md">docs/third-party/BUSYBOX.md</a>
 */
package io.simpleide.ui.terminal;
