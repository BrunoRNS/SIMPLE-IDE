package io.simpleide.frame.log;

import java.awt.Color;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback appender that forwards events to a {@link LogPanel}.
 *
 * <p>
 * Covered by {@code SwingLogAppenderTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/logging.md">docs/api/ui/logging.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class SwingLogAppender extends AppenderBase<ILoggingEvent> {

	private final LogPanel logPanel;

	/**
	 * Creates an appender that writes to the supplied panel.
	 * @param logPanel destination log panel
	 */
	public SwingLogAppender(LogPanel logPanel) {
		this.logPanel = logPanel;
	}

	/**
	 * Formats a Logback event and appends it to the panel with its level color.
	 * @param event event to append
	 */
	@Override
	protected void append(ILoggingEvent event) {
		Color color = LogLevelColorMapping.colorForLevel(event.getLevel());
		logPanel.appendEntry(format(event), event.getLevel().levelStr, color);
	}

	private String format(ILoggingEvent event) {
		return String.format("[%s] %s", event.getLoggerName(), event.getFormattedMessage());
	}

}
