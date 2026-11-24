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
 * Read-only audio player that embeds JavaFX media playback in Swing.
 *
 * <p>
 * The JavaFX media pipeline loads media asynchronously. {@link Media} and
 * {@link MediaPlayer} are created on the JavaFX application thread, while loading errors
 * are reported on the status label instead of failing silently.
 *
 * <p>
 * This class is compiled only on platforms that publish JavaFX native artifacts (Linux
 * x64/arm64, macOS x64/arm64 and Windows x64). Use {@link MediaPlaybacks#audio(Path)} to
 * create it through the platform-neutral API; that factory returns {@code null} where
 * playback is unavailable.
 *
 * @author BrunoRNS
 * @since v1.1.0
 * @see MediaPlaybacks
 */
public final class AudioViewer extends JPanel implements MediaPlayback {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioViewer.class);

	private final Path path;

	private final JButton play = new JButton("Play");

	private final JButton pause = new JButton("Pause");

	private final JButton stop = new JButton("Stop");

	private final JSlider progress = new JSlider(0, 1000, 0);

	private final JSlider volume = new JSlider(0, 100, 80);

	private final JLabel status = new JLabel("Ready", SwingConstants.CENTER);

	private JFXPanel fxPanel;

	private volatile MediaPlayer player;

	private volatile boolean closed;

	private boolean updatingProgress;

	/**
	 * Creates a player for the specified audio file.
	 * @param path audio file to play
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public AudioViewer(Path path) {
		super(new BorderLayout(8, 8));
		this.path = Objects.requireNonNull(path, "path");
		JPanel controls = new JPanel();
		controls.add(play);
		controls.add(pause);
		controls.add(stop);
		JPanel sliders = new JPanel(new BorderLayout(8, 0));
		sliders.add(new JLabel("Progress"), BorderLayout.WEST);
		sliders.add(progress, BorderLayout.CENTER);
		JPanel volumePanel = new JPanel(new BorderLayout(8, 0));
		volumePanel.add(new JLabel("Volume"), BorderLayout.WEST);
		volumePanel.add(volume, BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout());
		footer.add(sliders, BorderLayout.CENTER);
		footer.add(volumePanel, BorderLayout.SOUTH);
		add(status, BorderLayout.CENTER);
		add(controls, BorderLayout.NORTH);
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
			if (player != null) {
				player.stop();
				player.seek(Duration.ZERO);
			}
		}));
		volume.addChangeListener(event -> onFx(() -> {
			if (player != null)
				player.setVolume(volume.getValue() / 100.0);
		}));
		progress.addChangeListener(event -> {
			if (!updatingProgress && !progress.getValueIsAdjusting() && player != null
					&& player.getTotalDuration() != null) {
				onFx(() -> player.seek(player.getTotalDuration().multiply(progress.getValue() / 1000.0)));
			}
		});
		if (GraphicsEnvironment.isHeadless()) {
			status.setText("Audio playback requires a graphical environment.");
		}
		else {
			try {
				JavaFxAudioOutput.configurePulseFallback();
				fxPanel = new JFXPanel();
			}
			catch (Throwable error) {
				LOGGER.warn("Unable to initialize JavaFX audio output for {}.", path, error);
				status.setText("Audio playback unavailable: unable to initialize audio output.");
				return;
			}
			Platform.runLater(this::loadMedia);
		}
	}

	/**
	 * Returns this panel so it can be mounted through {@link MediaPlayback}.
	 * @return this component
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	@Override
	public JComponent component() {
		return this;
	}

	/**
	 * Returns whether JavaFX has created the media player.
	 * @return {@code true} after JavaFX media initialization succeeds
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean isMediaLoaded() {
		return player != null;
	}

	/**
	 * Starts playback.
	 * @author BrunoRNS
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
	 * @author BrunoRNS
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
	 * @author BrunoRNS
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
	 * @author BrunoRNS
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
	 * @author BrunoRNS
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
			disposed.await(5, TimeUnit.SECONDS);
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
		if (current != null) {
			current.stop();
			current.dispose();
			player = null;
		}
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
		applyMedia(player);
	}

	/**
	 * Installs a loaded media player. Runs on the JavaFX application thread.
	 */
	private void applyMedia(MediaPlayer player) {
		if (closed) {
			player.dispose();
			return;
		}
		this.player = player;
		// The MediaView is not shown for audio, but keeping the player attached to a
		// scene keeps the JavaFX media pipeline alive for the duration of playback.
		MediaView view = new MediaView(player);
		BorderPane root = new BorderPane(view);
		root.setStyle("-fx-background-color: #111111;");
		fxPanel.setScene(new Scene(root));
		player.setVolume(volume.getValue() / 100.0);
		player.currentTimeProperty()
			.addListener((observable, oldTime, current) -> SwingUtilities.invokeLater(() -> updateProgress(current)));
		player.statusProperty()
			.addListener((observable, oldStatus, current) -> SwingUtilities
				.invokeLater(() -> status.setText(current.toString())));
	}

	/**
	 * Reports a media loading error to the log and to the status label on the Swing EDT.
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
			status.setText("Unable to play: " + message);
		});
	}

	private static String messageOf(Throwable error) {
		if (error == null) {
			return "unknown error";
		}
		String message = error.getMessage();
		return message == null ? error.getClass().getName() : message;
	}

	private void updateProgress(Duration current) {
		if (player == null || player.getTotalDuration() == null || player.getTotalDuration().isUnknown()
				|| player.getTotalDuration().isIndefinite() || player.getTotalDuration().toMillis() <= 0) {
			return;
		}
		updatingProgress = true;
		try {
			progress.setValue((int) (current.toMillis() * 1000 / player.getTotalDuration().toMillis()));
		}
		finally {
			updatingProgress = false;
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
