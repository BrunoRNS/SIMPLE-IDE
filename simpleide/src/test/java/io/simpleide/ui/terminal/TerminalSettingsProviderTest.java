package io.simpleide.ui.terminal;

import java.awt.Font;
import java.util.Objects;

import javax.swing.UIManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;

class TerminalSettingsProviderTest {

    @Test
    void followsCurrentUiFontAndColors() {
        Object originalFont = UIManager.get("TextArea.font");
        Object originalForeground = UIManager.get("TextArea.foreground");
        Object originalBackground = UIManager.get("TextArea.background");
        try {
            TerminalSettingsProvider settings = new TerminalSettingsProvider();
            UIManager.put("TextArea.font", new Font(Font.DIALOG, Font.BOLD, 19));
            assertEquals(19, settings.getTerminalFont().getSize());
            assertEquals(Font.BOLD, settings.getTerminalFont().getStyle());

            java.awt.Color foreground = new java.awt.Color(12, 34, 56);
            java.awt.Color background = new java.awt.Color(78, 90, 123);
            UIManager.put("TextArea.foreground", foreground);
            UIManager.put("TextArea.background", background);
            TextStyle style = Objects.requireNonNull(settings.getDefaultStyle());
            assertEquals(foreground.getRGB(), resolveColor(style.getForeground()));
            assertEquals(background.getRGB(), resolveColor(style.getBackground()));

            UIManager.put("TextArea.background", java.awt.Color.BLACK);
            assertEquals(
                java.awt.Color.BLACK.getRGB(),
                resolveColor(settings.getDefaultStyle().getBackground())
            );
        } finally {
            restoreUiValue("TextArea.font", originalFont);
            restoreUiValue("TextArea.foreground", originalForeground);
            restoreUiValue("TextArea.background", originalBackground);
        }
    }

    private static int resolveColor(TerminalColor terminalColor) {
        Color color = Objects.requireNonNull(terminalColor, "Terminal color must not be null").toColor();
        return Objects.requireNonNull(color, "RGB color must not be null").getRGB();
    }

    private static void restoreUiValue(String key, Object value) {
        if (value == null) {
            UIManager.getDefaults().remove(key);
        } else {
            UIManager.put(key, value);
        }
    }
}
