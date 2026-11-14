/**
 * Desktop application frame and its supporting UI packages.
 *
 * <p>
 * {@link io.simpleide.frame.MainFrame} is the composition root of the desktop
 * application. Created on the Swing Event Dispatch Thread by an {@code EDTNode}, it
 * assembles the project tree and file watcher, the editor tab set, the embedded terminal,
 * the debug console, the LSP status bar and the menu bar. It also installs the Logback
 * appender that feeds the console and performs an orderly shutdown when the window
 * closes.
 *
 * <p>
 * Supporting packages provide the layout helpers ({@code io.simpleide.frame.layout}), the
 * Swing log console ({@code io.simpleide.frame.log}), modal dialogs
 * ({@code io.simpleide.frame.popup}) and icon loading ({@code io.simpleide.frame.utils})
 * used by the frame.
 *
 * <p>
 * Every component is created on the EDT. Background work such as loading the project tree
 * or changing the root directory runs on managed nodes and applies its result with
 * {@code ThreadMaster.executeOnEDT}. Closing the frame stops the watcher, closes the
 * editor manager and shuts the thread manager down, so the frame must not be embedded in
 * an application that does not own those resources.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 */
package io.simpleide.frame;
