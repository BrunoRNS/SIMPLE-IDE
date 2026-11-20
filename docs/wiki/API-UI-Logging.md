# Logging console

`io.simpleide.frame.log` renders application logs inside the desktop window. The
debug console is one of the output tabs created by `MainFrame`.

## Components

| Class | Responsibility |
| --- | --- |
| `LogPanel` | Scrolling text pane that displays log lines |
| `SwingLogAppender` | Logback appender that forwards events to a `LogPanel` |
| `LogLevelColorMapping` | Maps Logback levels to text colors |

## How it is wired

`MainFrame.attachLogAppender` creates a `SwingLogAppender`, attaches it to the
Logback root logger context and starts it. From that point every log event at the
configured level is appended to the debug console. The frame also raises the
`com.jediterm` and `com.pty4j` loggers to `INFO` so terminal internals do not
flood the console at debug level.

```java
import io.simpleide.frame.log.LogPanel;
import io.simpleide.frame.log.SwingLogAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

LogPanel panel = new LogPanel();
SwingLogAppender appender = new SwingLogAppender(panel);
appender.setContext(((ch.qos.logback.classic.Logger)
    LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).getLoggerContext());
appender.start();
```

Detach and stop the appender before discarding the panel; a running appender keeps
a reference to it.

## Color mapping

`LogLevelColorMapping` maps `TRACE`, `DEBUG`, `INFO`, `WARN` and `ERROR` to
readable colors for the active theme. The mapping is applied by the panel when a
line is appended, so changing it affects new lines only.

## Threading

Log events can arrive from any thread, including managed worker pools and LSP
reader threads. `SwingLogAppender` marshals updates to the Swing Event Dispatch
Thread so the panel is only mutated on the EDT. Do not call `LogPanel` mutation
methods directly from a background thread.

## Testing

The behavior is covered by `LogPanelTest`, `SwingLogAppenderTest` and
`LogLevelColorMappingTest` under `simpleide/src/test/java/io/simpleide/frame/log`.
