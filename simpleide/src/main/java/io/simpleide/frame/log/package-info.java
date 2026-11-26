/**
 * Swing-based log console used by the desktop application.
 *
 * <p>
 * {@link io.simpleide.frame.log.LogPanel} is a read-only text pane that renders
 * color-coded log entries and keeps its background aligned with the active look and feel.
 * {@link io.simpleide.frame.log.SwingLogAppender} is a Logback appender that forwards
 * events to the panel, and {@link io.simpleide.frame.log.LogLevelColorMapping} maps
 * Logback levels to readable colors.
 *
 * <p>
 * {@code MainFrame} attaches one appender to the Logback root logger and adds the panel
 * to the "Debug Console" output tab. Log events can arrive from any thread, so the panel
 * schedules its updates on the Swing Event Dispatch Thread through the thread manager.
 * The appender keeps a reference to the panel; detach and stop it before discarding the
 * panel.
 *
 * <p>
 * The console is an application diagnostic view, not a logging backend for embedders. A
 * library that uses SLF4J does not need this package.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/logging.md">docs/api/ui/logging.md</a>
 */
package io.simpleide.frame.log;
