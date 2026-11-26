package io.simpleide.api;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import io.simpleide.thread.nodes.edt.listeners.ChangeNode;

/**
 * Swing image viewer for formats supported by the JDK ImageIO readers and installed
 * ImageIO plugins such as TwelveMonkeys and Batik.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class ImageViewer extends JPanel {

	private static final long MAX_IMAGE_PIXELS = 1_000_000;

	private final ImageCanvas canvas;

	private final JSlider zoom;

	private final int sourceWidth;

	private final int sourceHeight;

	/**
	 * Opens an image using the registered ImageIO readers.
	 * @param path image path
	 * @throws IOException if no reader can decode the file
	 */
	public ImageViewer(Path path) throws IOException {
		this(readImage(path));
	}

	private ImageViewer(DecodedImage decoded) {
		this(decoded.image, decoded.width, decoded.height);
	}

	/**
	 * Displays a decoded image.
	 * @param image image to render
	 */
	public ImageViewer(BufferedImage image) {
		this(image, image.getWidth(), image.getHeight());
	}

	/**
	 * Displays a bounded-resolution image while retaining its original dimensions.
	 * @param image decoded image to render
	 * @param sourceWidth original image width
	 * @param sourceHeight original image height
	 */
	public ImageViewer(BufferedImage image, int sourceWidth, int sourceHeight) {
		super(new BorderLayout());
		canvas = new ImageCanvas(Objects.requireNonNull(image, "image"));
		this.sourceWidth = sourceWidth;
		this.sourceHeight = sourceHeight;
		zoom = new JSlider(SwingConstants.HORIZONTAL, 25, 400, 100);
		zoom.setMajorTickSpacing(75);
		zoom.setMinorTickSpacing(25);
		zoom.setPaintTicks(true);
		zoom.setPaintLabels(true);
		zoom.addChangeListener(new ChangeNode(event -> canvas.setScale(zoom.getValue() / 100.0)));

		JPanel toolbar = new JPanel(new BorderLayout(8, 0));
		toolbar.add(new JLabel("Zoom"), BorderLayout.WEST);
		toolbar.add(zoom, BorderLayout.CENTER);
		add(toolbar, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		add(scroll, BorderLayout.CENTER);
	}

	private static DecodedImage readImage(Path path) throws IOException {
		Path imagePath = Objects.requireNonNull(path, "path");
		try (ImageInputStream input = javax.imageio.ImageIO.createImageInputStream(imagePath.toFile())) {
			if (input == null) {
				throw new IOException("No ImageIO reader can decode " + path);
			}
			Iterator<ImageReader> readers = javax.imageio.ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IOException("No ImageIO reader can decode " + path);
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				double pixels = (double) width * height;
				int subsampling = pixels <= MAX_IMAGE_PIXELS ? 1
						: Math.max(1, (int) Math.ceil(Math.sqrt(pixels / MAX_IMAGE_PIXELS)));
				ImageReadParam param = reader.getDefaultReadParam();
				if (subsampling > 1) {
					param.setSourceSubsampling(subsampling, subsampling, 0, 0);
				}
				BufferedImage image = reader.read(0, param);
				if (image == null) {
					throw new IOException("No ImageIO reader can decode " + path);
				}
				return new DecodedImage(image, width, height);
			}
			finally {
				reader.dispose();
			}
		}
	}

	private record DecodedImage(BufferedImage image, int width, int height) {
	}

	/** Returns the zoom percentage currently applied to the image. */
	public int zoomPercent() {
		return zoom.getValue();
	}

	/** Sets zoom from 25% through 400%. */
	public void setZoomPercent(int percent) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> setZoomPercent(percent));
			return;
		}
		zoom.setValue(percent);
	}

	/** Returns the source image width in pixels. */
	public int imageWidth() {
		return sourceWidth;
	}

	/** Returns the source image height in pixels. */
	public int imageHeight() {
		return sourceHeight;
	}

	/** Releases the decoded preview raster when the viewer is no longer needed. */
	public void releaseImage() {
		canvas.releaseImage();
	}

	private static final class ImageCanvas extends JPanel {

		private BufferedImage image;

		private double scale = 1.0;

		private ImageCanvas(BufferedImage image) {
			this.image = image;
			setOpaque(true);
		}

		private void setScale(double scale) {
			this.scale = scale;
			revalidate();
			repaint();
		}

		private void releaseImage() {
			image = null;
			revalidate();
			repaint();
		}

		@Override
		public Dimension getPreferredSize() {
			return image == null ? new Dimension(1, 1)
					: new Dimension(Math.max(1, (int) Math.ceil(image.getWidth() * scale)),
							Math.max(1, (int) Math.ceil(image.getHeight() * scale)));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (image == null) {
				return;
			}
			Graphics2D g2 = (Graphics2D) graphics.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
						scale < 1.0 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
								: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g2.drawImage(image, 0, 0, getPreferredSize().width, getPreferredSize().height, null);
			}
			finally {
				g2.dispose();
			}
		}

	}

}
