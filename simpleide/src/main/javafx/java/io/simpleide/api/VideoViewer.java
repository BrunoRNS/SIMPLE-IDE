package io.simpleide.api;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only video player that embeds JavaFX media playback in Swing.
 *
 * <p>
 * The JavaFX media pipeline loads media asynchronously. {@link Media} and
 * {@link MediaPlayer} are created on the JavaFX application thread, while loading errors
 * are reported on the status label instead of failing silently.
 *
 * <p>
 * This class is compiled only on platforms that publish JavaFX native artifacts and are
 * enabled by the selected Maven variant: Linux x64, macOS x64/arm64 and Windows x64.
 * Linux arm64 uses nojavafx automatically and supports forced JavaFX variants only
 * with glibc 2.34+ (Debian 12+). Use {@link MediaPlaybacks#video(Path)} to create it
 * through the platform-neutral API; that factory returns {@code null} where playback
 * is unavailable.
 *
 * @author luisotavio13
 * @since v1.1.0
 * @see MediaPlaybacks
 */
public final class VideoViewer extends JPanel implements MediaPlayback {

	private static final Logger LOGGER = LoggerFactory.getLogger(VideoViewer.class);

	private final Path path;

	private final JButton play = new JButton("Play");

	private final JButton pause = new JButton("Pause");

	private final JButton stop = new JButton("Stop");

	private final JSlider seek = new JSlider(0, 1000, 0);

	private final JSlider volume = new JSlider(0, 100, 80);

	private final JLabel status = new JLabel("Loading video...", SwingConstants.CENTER);

	private JFXPanel fxPanel;

	private volatile MediaPlayer player;

	private volatile Media media;

	private volatile boolean closed;

	private boolean updatingSeek;

	/**
	 * Creates a player for the specified video file.
	 * @param path video file to play
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	public VideoViewer(Path path) {
		super(new BorderLayout(8, 8));
		this.path = Objects.requireNonNull(path, "path");
		JPanel controls = new JPanel();
		controls.add(play);
		controls.add(pause);
		controls.add(stop);
		JPanel sliders = new JPanel(new BorderLayout(8, 0));
		sliders.add(new JLabel("Seek"), BorderLayout.WEST);
		sliders.add(seek, BorderLayout.CENTER);
		JPanel volumePanel = new JPanel(new BorderLayout(8, 0));
		volumePanel.add(new JLabel("Volume"), BorderLayout.WEST);
		volumePanel.add(volume, BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout());
		footer.add(sliders, BorderLayout.CENTER);
		footer.add(volumePanel, BorderLayout.SOUTH);
		add(controls, BorderLayout.NORTH);
		add(status, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
		play.addActionListener(event -> onFx(() -> {
			if (player != null)
				player.play();
		}));
		pause.addActionListener(event -> onFx(() -> {
			if (player != null)
				player.pause();
		}));
		stop.addActionListener(event -> onFx(() -> {
			if (player != null)
				player.stop();
		}));
		volume.addChangeListener(event -> onFx(() -> {
			if (player != null)
				player.setVolume(volume.getValue() / 100.0);
		}));
		seek.addChangeListener(event -> {
			if (!updatingSeek && !seek.getValueIsAdjusting()) {
				onFx(() -> {
					if (player != null && player.getTotalDuration() != null) {
						player.seek(player.getTotalDuration().multiply(seek.getValue() / 1000.0));
					}
				});
			}
		});
		if (GraphicsEnvironment.isHeadless()) {
			status.setText("Video playback requires a graphical environment.");
		}
		else {
			try {
				JavaFxAudioOutput.configureAlsaFallback();
				fxPanel = new JFXPanel();
			}
			catch (Throwable error) {
				LOGGER.warn("Unable to initialize JavaFX audio output for {}.", path, error);
				status.setText("Video playback unavailable: unable to initialize audio output.");
				return;
			}
			remove(status);
			add(fxPanel, BorderLayout.CENTER);
			Platform.runLater(this::loadMedia);
		}
	}

	/**
	 * Returns this panel so it can be mounted through {@link MediaPlayback}.
	 * @return this component
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	@Override
	public JComponent component() {
		return this;
	}

	/**
	 * Returns whether JavaFX has created the media player.
	 * @return {@code true} after JavaFX media initialization succeeds
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	public boolean isMediaLoaded() {
		return player != null;
	}

	/**
	 * Starts playback.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	@Override
	public void play() {
		onFx(() -> {
			if (player != null)
				player.play();
		});
	}

	/**
	 * Pauses playback while preserving the current position.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	@Override
	public void pause() {
		onFx(() -> {
			if (player != null)
				player.pause();
		});
	}

	/**
	 * Stops playback and returns to the beginning.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	@Override
	public void stop() {
		onFx(() -> {
			if (player != null) {
				player.stop();
				player.seek(Duration.ZERO);
			}
		});
	}

	/**
	 * Sets the playback volume between 0 (muted) and 1 (full volume).
	 * @param value volume from 0 through 1
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	@Override
	public void setVolume(float value) {
		onFx(() -> {
			if (player != null)
				player.setVolume(Math.max(0, Math.min(1, value)));
		});
	}

	/**
	 * Releases the JavaFX media player.
	 * @author luisotavio13
	 * @since v1.1.0
	 */
	@Override
	public void close() {
		closed = true;
		if (fxPanel == null) {
			return;
		}
		if (Platform.isFxApplicationThread()) {
			disposePlayer();
			return;
		}
		// Wait for disposal so callers can safely delete or replace the media file as
		// soon as close() returns. On Windows the player keeps the file locked until it
		// is disposed on the JavaFX thread.
		CountDownLatch disposed = new CountDownLatch(1);
		try {
			Platform.runLater(() -> {
				try {
					disposePlayer();
				}
				finally {
					disposed.countDown();
				}
			});
			disposed.await(10, TimeUnit.SECONDS);
		}
		catch (IllegalStateException error) {
			LOGGER.debug("JavaFX toolkit is already shut down; skipping media disposal.", error);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}

	private void disposePlayer() {
		MediaPlayer current = player;
		player = null;
		if (current != null) {
			current.stop();
			current.dispose();
		}
		// Detach the media graph before dropping the references. This also releases
		// the file handle on Windows, where MediaPlayer keeps the file locked until
		// the JavaFX scene is detached and the player is disposed.
		if (fxPanel != null) {
			try {
				fxPanel.setScene(null);
			}
			catch (RuntimeException | LinkageError error) {
				LOGGER.debug("Unable to detach the JavaFX video scene during disposal.", error);
			}
		}
		media = null;
	}

	/**
	 * Creates the media pipeline on the JavaFX application thread. JavaFX loads the media
	 * asynchronously.
	 */
	private void loadMedia() {
		if (closed) {
			return;
		}
		if (!Files.isReadable(path)) {
			reportLoadError("File is not readable: " + path, null);
			return;
		}
		Media media;
		try {
			media = new Media(path.toUri().toString());
		}
		catch (Throwable error) {
			reportLoadError("Unable to create media for " + path + ": " + messageOf(error), error);
			return;
		}
		if (closed) {
			return;
		}
		media.setOnError(() -> {
			if (closed) {
				return;
			}
			MediaException error = media.getError();
			reportLoadError("Media load failed for " + path + ": " + messageOf(error), error);
		});
		if (closed) {
			return;
		}
		MediaPlayer player;
		try {
			player = new MediaPlayer(media);
		}
		catch (Throwable error) {
			reportLoadError("Unable to create media player for " + path + ": " + messageOf(error), error);
			return;
		}
		if (closed) {
			player.dispose();
			return;
		}
		player.setOnError(() -> {
			if (closed) {
				return;
			}
			MediaException error = player.getError();
			reportLoadError("Media playback failed for " + path + ": " + messageOf(error), error);
		});
		player.setOnEndOfMedia(() -> {
			if (closed) {
				return;
			}
			onFx(() -> status.setText("Stopped"));
		});
		this.media = media;
		applyMedia(player);
	}

	/**
	 * Installs a loaded media player into the JavaFX panel. Runs on the JavaFX
	 * application thread.
	 */
	private void applyMedia(MediaPlayer player) {
		if (closed) {
			player.dispose();
			return;
		}
		this.player = player;
		MediaView view = new MediaView(player);
		view.setPreserveRatio(true);
		BorderPane root = new BorderPane(view);
		root.setStyle("-fx-background-color: #111111;");
		fxPanel.setScene(new Scene(root));
		player.setVolume(volume.getValue() / 100.0);
		player.currentTimeProperty()
			.addListener((observable, oldTime, current) -> SwingUtilities.invokeLater(() -> updateSeek(current)));
		player.statusProperty()
			.addListener((observable, oldStatus, current) -> SwingUtilities
				.invokeLater(() -> status.setText(current.toString())));
	}

	/**
	 * Reports a media loading error to the log and to the status label on the Swing EDT,
	 * then swaps out the JavaFX panel for the status label.
	 */
	private void reportLoadError(String message, Throwable error) {
		if (error != null) {
			LOGGER.warn("Unable to load media {}: {}", path, message, error);
		}
		else {
			LOGGER.warn("Unable to load media {}: {}", path, message);
		}
		SwingUtilities.invokeLater(() -> {
			if (closed) {
				return;
			}
			remove(fxPanel);
			status.setText("Unable to load video: " + message);
			add(status, BorderLayout.CENTER);
			revalidate();
			repaint();
		});
	}

	private static String messageOf(Throwable error) {
		if (error == null) {
			return "unknown error";
		}
		String message = error.getMessage();
		return message == null ? error.getClass().getName() : message;
	}

	private void updateSeek(Duration current) {
		if (player == null || player.getTotalDuration() == null || player.getTotalDuration().isUnknown()
				|| player.getTotalDuration().isIndefinite() || player.getTotalDuration().toMillis() <= 0) {
			return;
		}
		updatingSeek = true;
		try {
			seek.setValue((int) (current.toMillis() * 1000 / player.getTotalDuration().toMillis()));
		}
		finally {
			updatingSeek = false;
		}
	}

	private static void onFx(Runnable action) {
		if (Platform.isFxApplicationThread()) {
			action.run();
		}
		else if (!GraphicsEnvironment.isHeadless()) {
			Platform.runLater(action);
		}
	}

	/**
	 * Returns the current player for lifecycle tests.
	 * @return the player, or {@code null} before JavaFX initializes it
	 */
	MediaPlayer playerForTesting() {
		return player;
	}

}
