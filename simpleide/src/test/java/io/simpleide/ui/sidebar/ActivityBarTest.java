package io.simpleide.ui.sidebar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.simpleide.ui.icon.ThemedSvgIcon;

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

	private static List<JButton> buttons(ActivityBar bar) {
		List<JButton> buttons = new ArrayList<>();
		for (Component component : bar.getComponents()) {
			if (component instanceof JButton button) {
				buttons.add(button);
			}
		}
		return buttons;
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
