package io.simpleide.frame.log;

import ch.qos.logback.classic.Level;
import java.awt.Color;
import java.util.Map;

/**
 * Maps Logback log levels to the colors used by the IDE log console.
 *
 * <p>
 * Covered by {@code LogLevelColorMappingTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/logging.md">docs/api/ui/logging.md</a>
 */
public class LogLevelColorMapping {

	private static final Map<Level, Color> LEVEL_COLORS = Map.of(Level.TRACE, Color.GRAY, Level.DEBUG, Color.CYAN,
			Level.INFO, Color.BLACK, Level.WARN, Color.ORANGE, Level.ERROR, Color.RED);

	/**
	 * Returns the display color for a log level.
	 * @param level level to look up
	 * @return the configured color, or {@code null} when the level has no mapping
	 */
	public static Color colorForLevel(Level level) {
		return LEVEL_COLORS.get(level);
	}

}
