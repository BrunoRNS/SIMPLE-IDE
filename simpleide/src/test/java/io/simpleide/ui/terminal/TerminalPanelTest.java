package io.simpleide.ui.terminal;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JScrollBar;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.SelectionUtil;
import com.jediterm.terminal.model.TerminalSelection;
import org.junit.jupiter.api.Test;

import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.menubar.ThemeManager;

/**
 * Tests {@link io.simpleide.ui.terminal.TerminalPanel}.
 */
class TerminalPanelTest {

	@Test
	void actionIconSvgColorsFollowTheTheme() throws Exception {
		Object originalForeground = UIManager.get("Button.foreground");
		Object originalBackground = UIManager.get("Button.background");
		Object originalPanelBackground = UIManager.get("Panel.background");
		Object originalAccent = UIManager.get("Component.accentColor");
		try {
			Color foreground = new Color(0xEBDBB2);
			Color background = new Color(0x3C3836);
			UIManager.put("Button.foreground", foreground);
			UIManager.put("Button.background", background);
			UIManager.put("Panel.background", background);
			UIManager.put("Component.accentColor", foreground);

			SwingUtilities.invokeAndWait(() -> {
				TerminalPanel panel = new TerminalPanel();
				assertThat(findButtons(panel)).isNotEmpty().allSatisfy(button -> {
					assertThat(button.getBackground()).as("button background follows active theme")
						.isEqualTo(background);
					assertThat(button.getForeground()).as("button foreground follows active theme")
						.isEqualTo(foreground);
					assertThat(button.getClientProperty("JButton.buttonType"))
						.as("terminal action buttons do not use rounded styling")
						.isNull();
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
			restoreUiValue("Panel.background", originalPanelBackground);
			restoreUiValue("Component.accentColor", originalAccent);
		}
	}

	@Test
	void terminalActionIconsKeepDarkVisibleStrokesInLightThemes() throws Exception {
		Object originalForeground = UIManager.get("Button.foreground");
		Object originalBackground = UIManager.get("Button.background");
		Object originalPanelBackground = UIManager.get("Panel.background");
		Object originalAccent = UIManager.get("Component.accentColor");
		try {
			for (ThemeManager.ThemeType theme : ThemeManager.ThemeType.values()) {
				ThemePalette palette = ThemePalette.of(theme);
				if (palette.dark()) {
					continue;
				}
				UIManager.put("Button.foreground", palette.buttonForeground());
				UIManager.put("Button.background", palette.surface());
				UIManager.put("Panel.background", palette.surface());
				UIManager.put("Component.accentColor", palette.accent());

				SwingUtilities.invokeAndWait(() -> {
					TerminalPanel panel = new TerminalPanel();
					assertThat(findButtons(panel)).hasSize(2).allSatisfy(button -> {
						Color background = button.getBackground();
						FlatSVGIcon icon = (FlatSVGIcon) button.getIcon();
						Color stroke = icon.getColorFilter().filter(button, new Color(0x6E7781));
						Color expectedStroke = ThemeColors.readable(palette.accent(), background,
								ThemeColors.MIN_LARGE_TEXT_CONTRAST);
						assertThat(stroke).as("terminal strokes use the theme accent in %s", theme)
							.isEqualTo(expectedStroke);
						assertThat(ThemeColors.contrastRatio(stroke, background))
							.as("main SVG stroke contrast in %s", theme)
							.isGreaterThanOrEqualTo(ThemeColors.MIN_LARGE_TEXT_CONTRAST);
						assertThat(stroke).as("light-theme terminal strokes should be dark or blue")
							.isNotEqualTo(Color.WHITE);
						assertThat(ThemeColors.contrastRatio(icon.getColorFilter().filter(button, Color.WHITE), stroke))
							.as("add/close mark contrast against its badge in %s", theme)
							.isGreaterThanOrEqualTo(ThemeColors.MIN_LARGE_TEXT_CONTRAST);

						BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
								BufferedImage.TYPE_INT_ARGB);
						Graphics2D graphics = image.createGraphics();
						try {
							icon.paintIcon(button, graphics, 0, 0);
						}
						finally {
							graphics.dispose();
						}
						long visibleStrokePixels = 0;
						for (int y = 0; y < image.getHeight(); y++) {
							for (int x = 0; x < image.getWidth(); x++) {
								Color pixel = new Color(image.getRGB(x, y), true);
								if (pixel.getAlpha() > 220 && ThemeColors.contrastRatio(pixel, background) >= 3.0d) {
									visibleStrokePixels++;
								}
							}
						}
						assertThat(visibleStrokePixels).as("visible terminal icon strokes in %s", theme)
							.isGreaterThan(10);
					});
				});
			}
		}
		finally {
			restoreUiValue("Button.foreground", originalForeground);
			restoreUiValue("Button.background", originalBackground);
			restoreUiValue("Panel.background", originalPanelBackground);
			restoreUiValue("Component.accentColor", originalAccent);
		}
	}

	@Test
	void spacesTerminalActionsAndExplainsDisabledStates() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			TerminalPanel panel = new TerminalPanel();
			List<JButton> buttons = findButtons(panel);
			assertThat(buttons).hasSize(2);
			assertThat(buttons.get(0).getParent().getComponent(1).getPreferredSize().height).isEqualTo(8);
			assertThat(buttons.get(0).getParent().getInsets().left).isEqualTo(8);

			panel.setActionsEnabled(false, false, true);

			assertThat(buttons.get(0).isEnabled()).isFalse();
			assertThat(buttons.get(0).getToolTipText()).isEqualTo("Cannot add another terminal");
			assertThat(buttons.get(1).isEnabled()).isFalse();
			assertThat(buttons.get(1).getToolTipText()).isEqualTo("No terminal selected");
		});
	}

	@Test
	void hidesTerminalScrollbarWithoutRemovingItsScrollModel() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			TerminalView.ThemedTerminalWidget widget = new TerminalView.ThemedTerminalWidget(new StubTtyConnector());
			try {
				JScrollBar scrollBar = findScrollBar(widget);
				assertThat(scrollBar).isNotNull();
				assertThat(scrollBar.isVisible()).isFalse();
				assertThat(scrollBar.getPreferredSize().width).isZero();
				assertThat(scrollBar.getModel()).isNotNull();
			}
			finally {
				widget.close();
			}
		});
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

	@Test
	void forwardsTypedSpecialCharactersToTheConnector() throws Exception {
		RecordingTtyConnector connector = new RecordingTtyConnector();
		TerminalView.ThemedTerminalWidget widget = createStartedWidget(connector);
		try {
			com.jediterm.terminal.ui.TerminalPanel panel = widget.getTerminalPanel();
			String expected = "~\u00B4\u00E1\u00E9\u00ED\u00F3\u00FA\u00E3\u00F5\u00E7";

			await("the terminal key handler should be installed").atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				SwingUtilities.invokeAndWait(() -> {
					for (char typed : expected.toCharArray()) {
						panel.handleKeyEvent(new KeyEvent(panel, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
								KeyEvent.VK_UNDEFINED, typed));
					}
				});
				assertThat(connector.writes()).contains(expected);
			});
		}
		finally {
			closeWidget(widget);
		}
	}

	@Test
	void forwardsCommittedInputMethodCharactersToTheConnector() throws Exception {
		RecordingTtyConnector connector = new RecordingTtyConnector();
		TerminalView.ThemedTerminalWidget widget = createStartedWidget(connector);
		try {
			com.jediterm.terminal.ui.TerminalPanel panel = widget.getTerminalPanel();
			String expected = "\u00E1\u00E3\u00E7";

			await("the terminal should accept input method text").atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				SwingUtilities.invokeAndWait(() -> {
					AttributedString text = new AttributedString(expected);
					panel.dispatchEvent(new InputMethodEvent(panel, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
							text.getIterator(), expected.length(), null, null));
				});
				assertThat(connector.writes()).contains(expected);
			});
		}
		finally {
			closeWidget(widget);
		}
	}

	@Test
	void selectsTerminalTextWithTheMouse() throws Exception {
		RecordingTtyConnector connector = new RecordingTtyConnector("SELECT_ME ~ \u00B4\r\n");
		TerminalView.ThemedTerminalWidget widget = createStartedWidget(connector);
		try {
			com.jediterm.terminal.ui.TerminalPanel panel = widget.getTerminalPanel();
			await("the terminal text should be rendered").atMost(5, TimeUnit.SECONDS)
				.untilAsserted(() -> SwingUtilities.invokeAndWait(
						() -> assertThat(panel.getTerminalTextBuffer().getLine(0).getText()).contains("SELECT_ME")));

			SwingUtilities.invokeAndWait(() -> {
				panel.setSize(panel.getPixelWidth(), panel.getPixelHeight());
				panel.doLayout();
				int charWidth = Math.max(1, panel.getPixelWidth() / panel.getTerminalTextBuffer().getWidth());
				int charHeight = Math.max(1, panel.getPixelHeight() / panel.getTerminalTextBuffer().getHeight());
				long now = System.currentTimeMillis();
				int endX = charWidth * 10 + charWidth / 2;
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK,
						charWidth / 2, charHeight / 2, 1, false, MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, now + 1,
						InputEvent.BUTTON1_DOWN_MASK, endX, charHeight / 2, 1, false, MouseEvent.BUTTON1));
				panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, now + 2, 0, endX, charHeight / 2,
						1, false, MouseEvent.BUTTON1));
			});

			TerminalSelection selection = panel.getSelection();
			assertThat(selection).isNotNull();
			assertThat(SelectionUtil.getSelectionText(selection, panel.getTerminalTextBuffer())).contains("SELECT_ME");
		}
		finally {
			closeWidget(widget);
		}
	}

	@Test
	void copiesAndPastesWithCtrlShiftShortcuts() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Clipboard access requires a display");

		RecordingTtyConnector connector = new RecordingTtyConnector("SELECT_ME ~ \u00B4\r\n");
		TerminalView.ThemedTerminalWidget widget = createStartedWidget(connector);
		try {
			com.jediterm.terminal.ui.TerminalPanel panel = widget.getTerminalPanel();
			await("the terminal text should be rendered").atMost(5, TimeUnit.SECONDS)
				.untilAsserted(() -> SwingUtilities.invokeAndWait(
						() -> assertThat(panel.getTerminalTextBuffer().getLine(0).getText()).contains("SELECT_ME")));

			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			SwingUtilities.invokeAndWait(() -> panel.selectAll());
			SwingUtilities.invokeAndWait(() -> clipboard.setContents(new StringSelection("initial"), null));

			SwingUtilities.invokeAndWait(
					() -> panel.handleKeyEvent(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
							InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_C, 'C')));
			assertThat((String) clipboard.getData(DataFlavor.stringFlavor)).contains("SELECT_ME");

			String pasted = "PASTE ~ \u00B4 \u00E7";
			SwingUtilities.invokeAndWait(() -> clipboard.setContents(new StringSelection(pasted), null));
			connector.clearWrites();
			SwingUtilities.invokeAndWait(
					() -> panel.handleKeyEvent(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
							InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_V, 'V')));
			await("the pasted text should reach the connector").atMost(5, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(connector.writes()).isEqualTo(pasted));
		}
		finally {
			closeWidget(widget);
		}
	}

	private static TerminalView.ThemedTerminalWidget createStartedWidget(RecordingTtyConnector connector)
			throws Exception {
		AtomicReference<TerminalView.ThemedTerminalWidget> reference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			TerminalView.ThemedTerminalWidget widget = new TerminalView.ThemedTerminalWidget(connector);
			reference.set(widget);
			widget.start();
		});
		return reference.get();
	}

	private static void closeWidget(TerminalView.ThemedTerminalWidget widget) throws Exception {
		SwingUtilities.invokeAndWait(widget::close);
	}

	private static final class RecordingTtyConnector implements TtyConnector {

		private final String initialOutput;

		private final StringBuilder writes = new StringBuilder();

		private final CountDownLatch closed = new CountDownLatch(1);

		private boolean outputSent;

		private volatile boolean connected = true;

		private RecordingTtyConnector() {
			this(null);
		}

		private RecordingTtyConnector(String initialOutput) {
			this.initialOutput = initialOutput;
		}

		@Override
		public int read(char[] buffer, int offset, int length) throws IOException {
			if (!outputSent && initialOutput != null) {
				outputSent = true;
				int count = Math.min(length, initialOutput.length());
				initialOutput.getChars(0, count, buffer, offset);
				return count;
			}
			try {
				closed.await();
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			return -1;
		}

		@Override
		public void write(byte[] bytes) {
			write(new String(bytes, StandardCharsets.UTF_8));
		}

		@Override
		public void write(String string) {
			synchronized (writes) {
				writes.append(string);
			}
		}

		@Override
		public boolean isConnected() {
			return connected;
		}

		@Override
		public int waitFor() throws InterruptedException {
			closed.await();
			return 0;
		}

		@Override
		public boolean ready() {
			return true;
		}

		@Override
		public String getName() {
			return "recording";
		}

		@Override
		public void close() {
			connected = false;
			closed.countDown();
		}

		private String writes() {
			synchronized (writes) {
				return writes.toString();
			}
		}

		private void clearWrites() {
			synchronized (writes) {
				writes.setLength(0);
			}
		}

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

	private static JScrollBar findScrollBar(Component component) {
		if (component instanceof JScrollBar scrollBar) {
			return scrollBar;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				JScrollBar result = findScrollBar(child);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	private static final class StubTtyConnector implements TtyConnector {

		@Override
		public int read(char[] buffer, int offset, int length) {
			return -1;
		}

		@Override
		public void write(byte[] bytes) {
		}

		@Override
		public void write(String string) {
		}

		@Override
		public boolean isConnected() {
			return false;
		}

		@Override
		public int waitFor() {
			return 0;
		}

		@Override
		public boolean ready() {
			return false;
		}

		@Override
		public String getName() {
			return "test";
		}

		@Override
		public void close() {
		}

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
