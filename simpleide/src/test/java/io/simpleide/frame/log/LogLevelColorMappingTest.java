package io.simpleide.frame.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import java.awt.Color;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogLevelColorMappingTest {
  private static final Map<Level, Color> LEVEL_COLORS =
      Map.of(
          Level.TRACE, Color.GRAY,
          Level.DEBUG, Color.CYAN,
          Level.INFO, Color.BLACK,
          Level.WARN, Color.ORANGE,
          Level.ERROR, Color.RED);

  @Test
  void shouldReturnCorrectColorForEachLevel() {
    for (Map.Entry<Level, Color> entry : LEVEL_COLORS.entrySet()) {
      assertEquals(entry.getValue(), LogLevelColorMapping.colorForLevel(entry.getKey()));
    }
  }
}
