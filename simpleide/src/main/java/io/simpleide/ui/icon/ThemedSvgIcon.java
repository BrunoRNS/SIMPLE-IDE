package io.simpleide.ui.icon;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.swing.Icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SVG icon that keeps a fixed artwork ratio and recolours its {@code currentColor} when
 * it is painted.
 *
 * <p>
 * The SVG source is read from the classpath with {@link ResourceUtil}, so it also works
 * when the application runs from a jar. On every paint the {@code currentColor} keyword
 * of the artwork is replaced by the colour supplied by the owner, which lets a single
 * icon follow the current theme and the selected state without being recreated. The
 * artwork is scaled into a box whose width is {@value #WIDTH_RATIO} of its height, the
 * 9:16 format of the bundled activity bar resources, and it is never stretched.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ThemedSvgIcon implements Icon {

	private static final Logger LOGGER = LoggerFactory.getLogger(ThemedSvgIcon.class);

	/**
	 * Width to height ratio of the bundled activity bar artwork.
	 */
	public static final double WIDTH_RATIO = 9.0 / 16.0;

	private static final String CURRENT_COLOR = "currentColor";

	private static final Map<String, String> SOURCES = new ConcurrentHashMap<>();

	private static final Map<Key, Icon> CACHE = new ConcurrentHashMap<>();

	private final String packageName;

	private final String fileName;

	private final int width;

	private final int height;

	private final Supplier<Color> tint;

	/**
	 * Creates an icon for a classpath SVG resource.
	 * @param packageName package that contains the resource, such as
	 * {@code io.simpleide.icon}
	 * @param fileName resource file name, such as {@code simpleide-explorer-9x16.svg}
	 * @param height icon height in pixels; the width follows the 9:16 ratio
	 * @param tint supplies the {@code currentColor} used on every paint, may return
	 * {@code null}
	 */
	public ThemedSvgIcon(String packageName, String fileName, int height, Supplier<Color> tint) {
		this.packageName = Objects.requireNonNull(packageName, "packageName");
		this.fileName = Objects.requireNonNull(fileName, "fileName");
		this.height = Math.max(1, height);
		this.width = Math.max(1, (int) Math.round(this.height * WIDTH_RATIO));
		this.tint = Objects.requireNonNull(tint, "tint");
	}

	@Override
	public void paintIcon(Component component, Graphics graphics, int x, int y) {
		icon().paintIcon(component, graphics, x, y);
	}

	@Override
	public int getIconWidth() {
		return width;
	}

	@Override
	public int getIconHeight() {
		return height;
	}

	private Icon icon() {
		Color resolved = tint.get();
		Color color = resolved == null ? Color.GRAY : resolved;
		return CACHE.computeIfAbsent(new Key(packageName, fileName, width, height, color.getRGB()),
				key -> create(color));
	}

	private Icon create(Color color) {
		String svg = source().replace(CURRENT_COLOR, toHex(color));
		if (svg.isBlank()) {
			return new FallbackIcon(width, height, color);
		}
		try {
			FlatSVGIcon artwork = new FlatSVGIcon(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
			return new ScaledIcon(artwork, width, height);
		}
		catch (IOException | RuntimeException error) {
			LOGGER.warn("Unable to render the SVG icon {}.", fileName, error);
			return new FallbackIcon(width, height, color);
		}
	}

	private String source() {
		return SOURCES.computeIfAbsent(packageName + '/' + fileName, ignored -> readSource());
	}

	private String readSource() {
		String resourceName = packageName.replace('.', '/') + '/' + fileName;
		try (InputStream input = ThemedSvgIcon.class.getClassLoader().getResourceAsStream(resourceName)) {
			if (input == null) {
				throw new IOException("Icon resource not found: " + resourceName);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException | IllegalArgumentException error) {
			LOGGER.warn("Unable to read the SVG icon {}.", fileName, error);
			return "";
		}
	}

	private static String toHex(Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private record Key(String packageName, String fileName, int width, int height, int rgb) {
	}

	/**
	 * Scales the artwork into the requested box without stretching it.
	 */
	private record ScaledIcon(Icon artwork, int width, int height) implements Icon {

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D scaled = (Graphics2D) graphics.create();
			try {
				scaled.translate(x, y);
				scaled.scale(width / (double) artwork.getIconWidth(), height / (double) artwork.getIconHeight());
				artwork.paintIcon(component, scaled, 0, 0);
			}
			finally {
				scaled.dispose();
			}
		}

		@Override
		public int getIconWidth() {
			return width;
		}

		@Override
		public int getIconHeight() {
			return height;
		}

	}

	/**
	 * Empty placeholder used when the artwork cannot be rendered.
	 */
	private record FallbackIcon(int width, int height, Color color) implements Icon {

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D marker = (Graphics2D) graphics.create();
			try {
				marker.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				marker.setColor(color);
				int size = Math.max(6, Math.min(width, height) / 2);
				int left = x + (width - size) / 2;
				int top = y + (height - size) / 2;
				marker.drawOval(left, top, size, size);
				marker.fillRoundRect(x + width / 2 - 1, top + size / 4, 2, size / 3, 2, 2);
				marker.fillOval(x + width / 2 - 1, top + size * 2 / 3, 2, 2);
			}
			finally {
				marker.dispose();
			}
		}

		@Override
		public int getIconWidth() {
			return width;
		}

		@Override
		public int getIconHeight() {
			return height;
		}

	}

}
