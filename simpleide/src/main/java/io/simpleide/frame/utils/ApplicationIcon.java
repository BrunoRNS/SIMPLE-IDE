
package io.simpleide.frame.utils;

import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Loads the bundled application icon for windows, taskbars and the system tray.
 *
 * @author LuisOtavio13
 * @since v1.1.0
 */
public final class ApplicationIcon {

	private static final String RESOURCE = "/io/simpleide/icon/simpleide_icon.png";

	private static final int[] WINDOW_ICON_SIZES = { 16, 20, 24, 32, 40, 48, 64, 128, 256 };

	private ApplicationIcon() {
	}

	public static BufferedImage loadImage() {
		try (InputStream input = ApplicationIcon.class.getResourceAsStream(RESOURCE)) {
			return input == null ? null : ImageIO.read(input);
		}
		catch (IOException ignored) {
			return null;
		}
	}

	public static BufferedImage loadImage(int size) {
		if (size < 1) {
			throw new IllegalArgumentException("Icon size must be positive");
		}

		BufferedImage source = loadImage();
		if (source == null) {
			return null;
		}

		double scale = Math.min(1.0, (double) size / Math.max(source.getWidth(), source.getHeight()));

		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}

		return scaled;
	}

	public static List<Image> loadWindowImages() {
		List<Image> images = new ArrayList<>(WINDOW_ICON_SIZES.length);

		for (int size : WINDOW_ICON_SIZES) {
			BufferedImage image = loadImage(size);
			if (image != null) {
				images.add(image);
			}
		}

		return List.copyOf(images);
	}

	public static void applyTo(Frame frame) {
		if (loadImage() == null) {
			return;
		}

		frame.setIconImages(loadWindowImages());
		setTaskbarIcon(loadImage(128));
	}

	public static void setTaskbarIcon() {
		setTaskbarIcon(loadImage(128));
	}

	private static void setTaskbarIcon(Image image) {
		if (image == null) {
			return;
		}

		try {
			if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
				Taskbar.getTaskbar().setIconImage(image);
			}
		}
		catch (UnsupportedOperationException | SecurityException ignored) {
			// The frame icon remains set on platforms without a taskbar or dock API.
		}
	}

}
