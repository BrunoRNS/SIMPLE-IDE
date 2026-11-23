package io.simpleide.ui.sidebar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.simpleide.ui.ThemeColors;
import io.simpleide.ui.ThemePalette;
import io.simpleide.ui.icon.ThemedSvgIcon;
import io.simpleide.ui.menubar.ThemeManager;

/**
 * Tests for {@link ActivityBar}.
 *
 * @author BrunoRNS
 */
class ActivityBarTest {

	@Test
	void usesTheNineBySixteenIcons() throws Exception {
		List<Icon> icons = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			for (JButton button : buttons(new ActivityBar())) {
				icons.add(button.getIcon());
			}
		});

		assertEquals(3, icons.size());
		for (Icon icon : icons) {
			assertSame(ThemedSvgIcon.class, icon.getClass());
			assertEquals(icon.getIconHeight() * 9, icon.getIconWidth() * 16, "9:16 ratio");
		}
	}

	@Test
	void selectingAViewKeepsTheButtonGeometry() throws Exception {
		Rectangle[] before = new Rectangle[1];
		Rectangle[] after = new Rectangle[1];
		Insets[] insets = new Insets[2];
		Icon[] icon = new Icon[2];
		SidebarView[] selected = new SidebarView[1];
		SwingUtilities.invokeAndWait(() -> {
			ActivityBar bar = new ActivityBar();
			JButton search = buttons(bar).get(1);
			before[0] = search.getBounds();
			insets[0] = search.getInsets();
			icon[0] = search.getIcon();

			bar.setSelected(SidebarView.SEARCH);

			after[0] = search.getBounds();
			insets[1] = search.getInsets();
			icon[1] = search.getIcon();
			selected[0] = bar.getSelected();
			bar.doLayout();
		});

		assertSame(SidebarView.SEARCH, selected[0]);
		assertEquals(before[0], after[0]);
		assertEquals(insets[0], insets[1]);
		assertSame(icon[0], icon[1]);
	}

	@Test
	void selectedAndHoveredActivityButtonsKeepTheirBackgroundTransparent() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			ActivityBar bar = new ActivityBar();
			JButton explorer = buttons(bar).get(0);

			bar.setSelected(SidebarView.EXPLORER);
			explorer.getModel().setRollover(true);

			for (JButton button : buttons(bar)) {
				assertFalse(button.isOpaque());
				assertFalse(button.isContentAreaFilled());
			}
		});
	}

	@Test
	void hoverAndSelectionTintOnlyTheActivityIcon() throws Exception {
		boolean[] colorsChanged = new boolean[2];
		SwingUtilities.invokeAndWait(() -> {
			ActivityBar bar = new ActivityBar();
			JButton explorer = buttons(bar).get(0);
			Icon icon = explorer.getIcon();

			bar.setSelected(SidebarView.SEARCH);
			int[] idle = paintIcon(icon, explorer);
			explorer.getModel().setRollover(true);
			int[] hovered = paintIcon(icon, explorer);
			explorer.getModel().setRollover(false);
			bar.setSelected(SidebarView.EXPLORER);
			int[] selected = paintIcon(icon, explorer);
			colorsChanged[0] = !Arrays.equals(idle, hovered);
			colorsChanged[1] = !Arrays.equals(idle, selected);
		});

		assertTrue(colorsChanged[0], "hover changes the icon tint");
		assertTrue(colorsChanged[1], "selection changes the icon tint");
	}

	@Test
	void paintsIconsAboveTheBarBackground() throws Exception {
		int[] visiblePixels = new int[1];
		SwingUtilities.invokeAndWait(() -> {
			ActivityBar bar = new ActivityBar();
			bar.setSize(48, 120);
			bar.doLayout();
			JButton search = buttons(bar).get(1);
			Rectangle bounds = search.getBounds();
			Color background = bar.getBackground();
			BufferedImage image = new BufferedImage(bar.getWidth(), bar.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try {
				bar.paint(graphics);
			}
			finally {
				graphics.dispose();
			}
			for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
				for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
					if (image.getRGB(x, y) != background.getRGB()) {
						visiblePixels[0]++;
					}
				}
			}
		});

		assertTrue(visiblePixels[0] > 50, "the unselected search icon is visible over the bar background");
	}

	@Test
	void keepsEveryActivityIconContrastedAcrossThemesAndSelections() throws Exception {
		String[] keys = { "Panel.background", "control", "Tree.background", "Label.foreground", "Panel.foreground",
				"Component.accentColor", "Component.focusColor" };
		Map<String, Object> defaults = new HashMap<>();
		for (String key : keys) {
			defaults.put(key, UIManager.get(key));
		}

		try {
			SwingUtilities.invokeAndWait(() -> {
				for (ThemeManager.ThemeType theme : ThemeManager.ThemeType.values()) {
					ThemePalette palette = ThemePalette.of(theme);
					UIManager.put("Panel.background", palette.surface());
					UIManager.put("control", palette.surface());
					UIManager.put("Tree.background", palette.background());
					UIManager.put("Label.foreground", palette.foreground());
					UIManager.put("Panel.foreground", palette.foreground());
					UIManager.put("Component.accentColor", palette.accent());
					UIManager.put("Component.focusColor", palette.accent());

					ActivityBar bar = new ActivityBar();
					List<JButton> activityButtons = buttons(bar);
					for (SidebarView selectedView : SidebarView.values()) {
						bar.setSelected(selectedView);
						for (int index = 0; index < activityButtons.size(); index++) {
							Color iconColor = dominantOpaqueColor(activityButtons.get(index).getIcon(),
									activityButtons.get(index));
							assertTrue(ThemeColors.contrastRatio(iconColor, palette.surface()) >= 3.0d,
									() -> "icon must contrast in " + theme + " with " + selectedView + " selected");
						}

						JButton selectedButton = activityButtons.get(selectedView.ordinal());
						Color selectedColor = dominantOpaqueColor(selectedButton.getIcon(), selectedButton);
						bar.setSelected(
								SidebarView.values()[(selectedView.ordinal() + 1) % SidebarView.values().length]);
						Color idleColor = dominantOpaqueColor(selectedButton.getIcon(), selectedButton);
						assertFalse(selectedColor.equals(idleColor),
								() -> "selection should visibly tint " + selectedView + " in " + theme);
					}
				}
			});
		}
		finally {
			SwingUtilities.invokeAndWait(() -> defaults.forEach(ActivityBarTest::restoreUiValue));
		}
	}

	private static List<JButton> buttons(ActivityBar bar) {
		List<JButton> buttons = new ArrayList<>();
		for (Component component : bar.getComponents()) {
			if (component instanceof JButton button) {
				buttons.add(button);
			}
		}
		return buttons;
	}

	private static Color dominantOpaqueColor(Icon icon, Component component) {
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			icon.paintIcon(component, graphics, 0, 0);
		}
		finally {
			graphics.dispose();
		}

		Map<Integer, Integer> frequencies = new HashMap<>();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getRGB(x, y);
				if ((pixel >>> 24) >= 230) {
					frequencies.merge(pixel & 0x00FFFFFF, 1, Integer::sum);
				}
			}
		}
		Map.Entry<Integer, Integer> dominant = frequencies.entrySet()
			.stream()
			.max(Map.Entry.comparingByValue())
			.orElseThrow(() -> new AssertionError("Activity icon should contain visible pixels."));
		assertTrue(dominant.getValue() > 10, "activity icon should contain a visible stroke");
		return new Color(dominant.getKey());
	}

	private static void restoreUiValue(String key, Object value) {
		if (value == null) {
			UIManager.getDefaults().remove(key);
		}
		else {
			UIManager.put(key, value);
		}
	}

	private static int[] paintIcon(Icon icon, Component component) {
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			icon.paintIcon(component, graphics, 0, 0);
		}
		finally {
			graphics.dispose();
		}
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

}
