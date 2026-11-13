package io.simpleide.ui.terminal;

import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.Test;

class TerminalPanelTest {

    @Test
    void selectsHighContrastIconColorsForLightDarkAndMidToneThemes() {
        Color[] backgrounds = {
            new Color(0x252525),
            new Color(0x777777),
            new Color(0xF5F5F5)
        };

        for (Color background : backgrounds) {
            Color foreground = TerminalPanel.getAccessibleIconForeground(background, background);
            assertThat(contrastRatio(foreground, background)).isGreaterThanOrEqualTo(4.5);
        }
    }

    @Test
    void preservesSessionNumbersAndOtherTabsWhenRemovingOneSession() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TerminalPanel panel = new TerminalPanel();
            List<JButton> actionButtons = findButtons(panel);
            assertThat(actionButtons).hasSize(2);
            for (JButton button : actionButtons) {
                assertThat(button.getIcon()).isInstanceOf(FlatSVGIcon.class);
                FlatSVGIcon icon = (FlatSVGIcon) button.getIcon();
                assertThat(icon.hasFound()).isTrue();
                assertThat(icon.getIconWidth()).isEqualTo(20);
                assertThat(icon.getIconHeight()).isEqualTo(20);
            }

            for (long id = 1; id <= TerminalPanel.MAX_TABS; id++) {
                panel.addTerminalTab(BigInteger.valueOf(id));
            }

            JTabbedPane tabs = findTabbedPane(panel);
            assertEquals(TerminalPanel.MAX_TABS, tabs.getTabCount());
            assertThatThrownBy(() -> panel.addTerminalTab(BigInteger.valueOf(6)))
                .isInstanceOf(IllegalStateException.class);
            Component firstTab = tabs.getComponentAt(0);
            Component secondTab = tabs.getComponentAt(1);
            Component fourthTab = tabs.getComponentAt(3);
            Component fifthTab = tabs.getComponentAt(4);

            panel.removeTerminal(BigInteger.valueOf(3));

            assertEquals(4, tabs.getTabCount());
            assertEquals("1", tabs.getTitleAt(0));
            assertEquals("2", tabs.getTitleAt(1));
            assertEquals("4", tabs.getTitleAt(2));
            assertEquals("5", tabs.getTitleAt(3));
            assertSame(firstTab, tabs.getComponentAt(0));
            assertSame(secondTab, tabs.getComponentAt(1));
            assertSame(fourthTab, tabs.getComponentAt(2));
            assertSame(fifthTab, tabs.getComponentAt(3));
            assertEquals(BigInteger.valueOf(5), panel.getSelectedSessionId());

            panel.addTerminalTab(BigInteger.valueOf(6));

            assertEquals(5, tabs.getTabCount());
            assertEquals("1", tabs.getTitleAt(0));
            assertEquals("2", tabs.getTitleAt(1));
            assertEquals("4", tabs.getTitleAt(2));
            assertEquals("5", tabs.getTitleAt(3));
            assertEquals("6", tabs.getTitleAt(4));
            assertEquals(BigInteger.valueOf(6), panel.getSelectedSessionId());
        });
    }

    @Test
    void supportsTerminalNumbersBeyondLongRange() throws Exception {
        BigInteger largeId = BigInteger.ONE.shiftLeft(80);
        SwingUtilities.invokeAndWait(() -> {
            TerminalPanel panel = new TerminalPanel();

            panel.addTerminalTab(largeId);

            assertEquals(largeId.toString(), findTabbedPane(panel).getTitleAt(0));
            assertEquals(largeId, panel.getSelectedSessionId());
        });
    }

    private static JTabbedPane findTabbedPane(Component component) {
        if (component instanceof JTabbedPane tabbedPane) {
            return tabbedPane;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                JTabbedPane result = findTabbedPane(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static List<JButton> findButtons(Component component) {
        List<JButton> buttons = new ArrayList<>();
        collectButtons(component, buttons);
        return buttons;
    }

    private static void collectButtons(Component component, List<JButton> buttons) {
        if (component instanceof JButton button) {
            buttons.add(button);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtons(child, buttons);
            }
        }
    }

    private static double contrastRatio(Color foreground, Color background) {
        double foregroundLuminance = relativeLuminance(foreground);
        double backgroundLuminance = relativeLuminance(background);
        double lighter = Math.max(foregroundLuminance, backgroundLuminance);
        double darker = Math.min(foregroundLuminance, backgroundLuminance);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(Color color) {
        return 0.2126 * linearize(color.getRed())
            + 0.7152 * linearize(color.getGreen())
            + 0.0722 * linearize(color.getBlue());
    }

    private static double linearize(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.04045
            ? normalized / 12.92
            : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
