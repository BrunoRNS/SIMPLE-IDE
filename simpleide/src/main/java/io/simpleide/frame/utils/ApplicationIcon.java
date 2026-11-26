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
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ApplicationIcon {

	private static final String RESOURCE = "/io/simpleide/icon/simpleide_icon.png";

	private static final int[] WINDOW_ICON_SIZES = { 16, 20, 24, 32, 40, 48, 64, 128, 256 };

	private ApplicationIcon() {
	}

	/**
	 * Loads the icon synchronously from the classpath, including when running from a JAR.
	 * @return the decoded icon image, or {@code null} when the resource is unavailable
	 */
	public static BufferedImage loadImage() {
		try (InputStream input = ApplicationIcon.class.getResourceAsStream(RESOURCE)) {
			return input == null ? null : ImageIO.read(input);
		}
		catch (IOException error) {
			return null;
		}
	}

	/**
	 * Loads a proportionally scaled copy of the bundled image.
	 * @param size maximum width and height in pixels
	 * @return the scaled icon, or {@code null} when the resource is unavailable
	 */
	public static BufferedImage loadImage(int size) {
		if (size < 1) {
			throw new IllegalArgumentException("Icon size must be positive");
		}
		BufferedImage source = loadImage();
		if (source == null) {
			return null;
		}
		int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scaleFactor(source, size)));
		int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scaleFactor(source, size)));
		BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
		}
		finally {
			graphics.dispose();
		}
		return scaled;
	}

	/**
	 * Supplies platform window managers with several reduced icon sizes.
	 * @return icon images from 16 to 256 pixels, or an empty list when the icon is
	 * missing
	 */
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

	/** Applies the bundled icon to a window and the operating system taskbar or dock. */
	public static void applyTo(Frame frame) {
		BufferedImage image = loadImage();
		if (image == null) {
			return;
		}
		frame.setIconImages(loadWindowImages());
		setTaskbarIcon(loadImage(128));
	}

	/**
	 * Applies the icon before the main window is created when the platform supports it.
	 */
	public static void setTaskbarIcon() {
		BufferedImage image = loadImage(128);
		if (image != null) {
			setTaskbarIcon(image);
		}
	}

	private static void setTaskbarIcon(Image image) {
		try {
			if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
				Taskbar.getTaskbar().setIconImage(image);
			}
		}
		catch (UnsupportedOperationException | SecurityException ignored) {
			// The frame icon remains set on platforms without a taskbar or dock API.
		}
	}

	private static double scaleFactor(BufferedImage source, int maximumSize) {
		return Math.min(1.0d, (double) maximumSize / Math.max(source.getWidth(), source.getHeight()));
	}

}
