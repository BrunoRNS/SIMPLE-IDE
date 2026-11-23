package io.simpleide.ui.icon;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JLabel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ThemedSvgIcon}.
 *
 * <p>
 * The icons are rendered into an image so the {@code currentColor} artwork and the masks
 * of the bundled SVG files are exercised without a display.
 *
 * @author BrunoRNS
 */
class ThemedSvgIconTest {

	private static final Color TINT = new Color(220, 40, 90);

	@Test
	void keepsTheNineBySixteenRatio() {
		ThemedSvgIcon icon = new ThemedSvgIcon("io.simpleide.icon", "simpleide-explorer-9x16.svg", 48, () -> TINT);

		assertEquals(48, icon.getIconHeight());
		assertEquals(27, icon.getIconWidth());
		assertEquals(18, widthOf("simpleide-search-9x16.svg", 32));
	}

	@Test
	void paintsEveryPixelWithTheResolvedTint() {
		BufferedImage image = render(
				new ThemedSvgIcon("io.simpleide.icon", "simpleide-explorer-9x16.svg", 48, () -> TINT));

		assertEquals(0, alpha(image, 0, 0));
		int solid = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				Color pixel = new Color(image.getRGB(x, y), true);
				if (pixel.getAlpha() < 16) {
					continue;
				}
				// Antialiasing may blend the edges, but the artwork itself must be
				// tinted.
				assertEquals(TINT.getRed(), pixel.getRed(), 8, "red at " + x + "," + y);
				assertEquals(TINT.getGreen(), pixel.getGreen(), 8, "green at " + x + "," + y);
				assertEquals(TINT.getBlue(), pixel.getBlue(), 8, "blue at " + x + "," + y);
				if (pixel.getAlpha() == 255) {
					solid++;
				}
			}
		}
		assertTrue(solid > 0, "the icon must paint its artwork");
	}

	@Test
	void keepsTheGearMaskTransparent() {
		BufferedImage image = render(
				new ThemedSvgIcon("io.simpleide.icon", "simpleide-settings-9x16.svg", 48, () -> TINT));

		assertEquals(0, alpha(image, image.getWidth() / 2, image.getHeight() / 2), "gear hole");
	}

	private static int widthOf(String fileName, int height) {
		return new ThemedSvgIcon("io.simpleide.icon", fileName, height, () -> TINT).getIconWidth();
	}

	private static int alpha(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) >>> 24;
	}

	private static BufferedImage render(javax.swing.Icon icon) {
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			icon.paintIcon(new JLabel(), graphics, 0, 0);
		}
		finally {
			graphics.dispose();
		}
		return image;
	}

}
