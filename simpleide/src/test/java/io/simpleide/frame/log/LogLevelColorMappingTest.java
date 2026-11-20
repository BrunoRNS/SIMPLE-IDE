package io.simpleide.frame.log;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.UIManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;

/**
 * Tests {@link io.simpleide.frame.log.LogLevelColorMapping}.
 */
class LogLevelColorMappingTest {

	private static final Map<Level, String> LEVEL_KEYS = new LinkedHashMap<>();

	static {
		LEVEL_KEYS.put(Level.TRACE, "Log.traceForeground");
		LEVEL_KEYS.put(Level.DEBUG, "Log.debugForeground");
		LEVEL_KEYS.put(Level.INFO, "Log.infoForeground");
		LEVEL_KEYS.put(Level.WARN, "Log.warnForeground");
		LEVEL_KEYS.put(Level.ERROR, "Log.errorForeground");
	}

	@Test
	void mapsEachLogLevelToItsThemeColor() {
		Map<String, Color> originalValues = new LinkedHashMap<>();
		Map<String, Color> expectedColors = new LinkedHashMap<>();
		int channel = 0x11;
		try {
			for (Map.Entry<Level, String> entry : LEVEL_KEYS.entrySet()) {
				Color color = new Color(channel, channel + 0x10, channel + 0x20);
				channel += 0x21;
				originalValues.put(entry.getValue(), UIManager.getColor(entry.getValue()));
				expectedColors.put(entry.getValue(), color);
				UIManager.put(entry.getValue(), color);
			}

			for (Map.Entry<Level, String> entry : LEVEL_KEYS.entrySet()) {
				assertEquals(expectedColors.get(entry.getValue()), LogLevelColorMapping.colorForLevel(entry.getKey()),
						"color for level " + entry.getKey());
			}
		}
		finally {
			restore(originalValues);
		}
	}

	@Test
	void followsThemeChanges() {
		Color original = UIManager.getColor("Log.errorForeground");
		try {
			Color darkRed = new Color(0xF44747);
			Color lightRed = new Color(0xE51400);
			UIManager.put("Log.errorForeground", darkRed);
			assertEquals(darkRed, LogLevelColorMapping.colorForLevel(Level.ERROR));

			UIManager.put("Log.errorForeground", lightRed);
			assertEquals(lightRed, LogLevelColorMapping.colorForLevel(Level.ERROR));
		}
		finally {
			if (original == null) {
				UIManager.getDefaults().remove("Log.errorForeground");
			}
			else {
				UIManager.put("Log.errorForeground", original);
			}
		}
	}

	@Test
	void returnsNullForLevelsWithoutAMapping() {
		assertNull(LogLevelColorMapping.colorForLevel(Level.OFF));
		assertNull(LogLevelColorMapping.colorForLevel(Level.TRACE));
	}

	private static void restore(Map<String, Color> originalValues) {
		originalValues.forEach((key, value) -> {
			if (value == null) {
				UIManager.getDefaults().remove(key);
			}
			else {
				UIManager.put(key, value);
			}
		});
	}

}
