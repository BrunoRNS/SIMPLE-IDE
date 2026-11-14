package io.simpleide.frame.log;

import ch.qos.logback.classic.Level;
import java.awt.Color;
import java.util.Map;

public class LogLevelColorMapping {
  private static final Map<Level, Color> LEVEL_COLORS =
      Map.of(
          Level.TRACE, Color.GRAY,
          Level.DEBUG, Color.CYAN,
          Level.INFO, Color.BLACK,
          Level.WARN, Color.ORANGE,
          Level.ERROR, Color.RED);

  public static Color colorForLevel(Level level) {
    return LEVEL_COLORS.get(level);
  }
}
