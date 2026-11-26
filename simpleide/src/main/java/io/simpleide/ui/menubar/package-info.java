/**
 * Application menu bar construction and theme management.
 *
 * <p>
 * {@link io.simpleide.ui.menubar.MenuBarFactory} installs an
 * {@link io.simpleide.ui.menubar.menus.IdeMenuBar} on a frame.
 * {@link io.simpleide.ui.menubar.ThemeManager} is the singleton that applies a FlatLaf
 * look and feel and an RSyntaxTextArea theme to every registered window and editor.
 *
 * <p>
 * Theme changes run on the Swing Event Dispatch Thread through the thread manager. The
 * manager reads bundled theme resources and is therefore not headless-safe, because it
 * updates open windows.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/menubar.md">docs/api/ui/menubar.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/menubar.md">docs/api/ui/menubar.md</a>
 */
package io.simpleide.ui.menubar;
