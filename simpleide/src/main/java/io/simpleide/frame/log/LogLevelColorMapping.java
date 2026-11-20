package io.simpleide.frame.log;

import ch.qos.logback.classic.Level;
import java.awt.Color;
import java.util.Map;
import javax.swing.UIManager;

/**
 * Maps Logback log levels to the colors used by the IDE log console.
 *
 * <p>
 * Covered by {@code LogLevelColorMappingTest}.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/logging.md">docs/api/ui/logging.md</a>
 */
public class LogLevelColorMapping {

	private static final Map<Level, String> LEVEL_KEYS = Map.of(Level.TRACE, "Log.traceForeground", Level.DEBUG,
			"Log.debugForeground", Level.INFO, "Log.infoForeground", Level.WARN, "Log.warnForeground", Level.ERROR,
			"Log.errorForeground");

	/**
	 * Returns the display color for a log level.
	 * @param level level to look up
	 * @return the theme color, or {@code null} when the level has no mapping
	 */
	public static Color colorForLevel(Level level) {
		String key = LEVEL_KEYS.get(level);
		return key == null ? null : UIManager.getColor(key);
	}

}
