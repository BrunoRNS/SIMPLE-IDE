package io.simpleide.ui.terminal;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * Tests {@link io.simpleide.ui.terminal.TerminalPanel}.
 */
class TerminalPanelTest {

	@Test
	void actionIconSvgColorsFollowTheTheme() throws Exception {
		Object originalForeground = UIManager.get("Button.foreground");
		Object originalBackground = UIManager.get("Button.background");
		try {
			Color foreground = new Color(0xEBDBB2);
			Color background = new Color(0x3C3836);
			UIManager.put("Button.foreground", foreground);
			UIManager.put("Button.background", background);

			SwingUtilities.invokeAndWait(() -> {
				TerminalPanel panel = new TerminalPanel();
				assertThat(findButtons(panel)).isNotEmpty().allSatisfy(button -> {
					assertThat(button.getBackground()).as("button background follows active theme")
						.isEqualTo(background);
					assertThat(button.getForeground()).as("button foreground follows active theme")
						.isEqualTo(foreground);
					assertThat(button.getClientProperty("JButton.buttonType"))
						.as("terminal action buttons use rounded styling")
						.isEqualTo("roundRect");
					FlatSVGIcon icon = (FlatSVGIcon) button.getIcon();
					FlatSVGIcon.ColorFilter filter = icon.getColorFilter();
					assertThat(filter.filter(null, new Color(0x6E7781))).as("accent stroke color")
						.isEqualTo(foreground);
					assertThat(filter.filter(null, Color.WHITE)).as("icon background color").isEqualTo(background);
					assertThat(filter.filter(null, Color.RED)).as("unmapped svg color").isEqualTo(Color.RED);
				});
			});
		}
		finally {
			restoreUiValue("Button.foreground", originalForeground);
			restoreUiValue("Button.background", originalBackground);
		}
	}

	@Test
	void refreshesTerminalActionIconSizesWhenTheConfiguredFontChanges() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			TerminalPanel panel = new TerminalPanel();
			Object originalFont = UIManager.get("Menu.font");
			try {
				UIManager.put("Menu.font", new FontUIResource(Font.DIALOG, Font.PLAIN, 22));
				SwingUtilities.updateComponentTreeUI(panel);
				assertThat(findButtons(panel)).allSatisfy(button -> {
					assertThat(button.getIcon().getIconWidth()).isEqualTo(22);
					assertThat(button.getIcon().getIconHeight()).isEqualTo(22);
				});
			}
			finally {
				restoreUiValue("Menu.font", originalFont);
				SwingUtilities.updateComponentTreeUI(panel);
			}
		});
	}

	@Test
	void preservesSessionNumbersAndOtherTabsWhenRemovingOneSession() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			TerminalPanel panel = new TerminalPanel();
			List<JButton> actionButtons = findButtons(panel);
			assertThat(actionButtons).hasSize(2);
			Font menuFont = UIManager.getFont("Menu.font");
			int expectedIconSize = Math.max(18, menuFont == null ? 18 : menuFont.getSize());
			for (JButton button : actionButtons) {
				assertThat(button.getIcon()).isInstanceOf(FlatSVGIcon.class);
				FlatSVGIcon icon = (FlatSVGIcon) button.getIcon();
				assertThat(icon.hasFound()).isTrue();
				assertThat(icon.getIconWidth()).isEqualTo(expectedIconSize);
				assertThat(icon.getIconHeight()).isEqualTo(expectedIconSize);
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

	private static void restoreUiValue(String key, Object value) {
		if (value == null) {
			UIManager.getDefaults().remove(key);
		}
		else {
			UIManager.put(key, value);
		}
	}

}
