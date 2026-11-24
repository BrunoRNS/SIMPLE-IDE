package io.simpleide.api;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for video playback loading and lifecycle.
 *
 * <p>
 * Playback tests require a graphical environment and a JavaFX media backend able to
 * decode the bundled sample; they are skipped when either is unavailable.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
class VideoViewerTest {

	@TempDir
	Path root;

	@Test
	void rejectsNullPath() {
		assertThatThrownBy(() -> new VideoViewer(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void returnsImmediatelyAndLoadsNothingForAMissingFile() {
		Path missing = root.resolve("inexistente.mp4");
		long start = System.nanoTime();
		try (VideoViewer viewer = new VideoViewer(missing)) {
			long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
			// JavaFX media initialization is queued asynchronously, so construction
			// returns immediately and nothing is loaded yet.
			assertThat(elapsedMillis).isLessThan(1000);
			assertThat(viewer.isMediaLoaded()).isFalse();
		}
	}

	@Test
	void returnsImmediatelyAndLoadsNothingForInvalidMediaBytes() throws Exception {
		Path invalid = Files.write(root.resolve("sample.mp4"), new byte[] { 1, 2, 3 });
		long start = System.nanoTime();
		try (VideoViewer viewer = new VideoViewer(invalid)) {
			long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
			assertThat(elapsedMillis).isLessThan(1000);
			assertThat(viewer.isMediaLoaded()).isFalse();
		}
	}

	@Test
	void loadAndPlayRealVideoUntilTheEnd() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Video playback requires a graphical environment.");
		Path sample = copyResourceToTemp("sample-video.mp4");
		// Abort when the JavaFX media backend is unavailable in this environment
		// (e.g. a headless CI without a GStreamer-backed JavaFX media provider).
		try {
			probeMediaBackend(sample);
		}
		catch (Throwable t) {
			Assumptions.abort("JavaFX media backend not available in this environment: " + t.getMessage());
		}
		try (VideoViewer viewer = new VideoViewer(sample)) {
			Awaitility.await()
				.atMost(10, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(viewer.isMediaLoaded()).isTrue());

			MediaPlayer player = viewer.playerForTesting();
			viewer.play();
			Awaitility.await()
				.atMost(10, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(player.getStatus()).isEqualTo(MediaPlayer.Status.PLAYING));

			// The bundled sample is 5 seconds long; playback should reach the end.
			Awaitility.await()
				.atMost(12, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(player.getCurrentTime().toMillis())
					.isGreaterThanOrEqualTo(player.getTotalDuration().toMillis() - 1000));
		}
	}

	@Test
	void closeIsIdempotentAndSafeBeforeMediaInitialization() {
		VideoViewer viewer = new VideoViewer(root.resolve("missing.mp4"));
		viewer.close();
		viewer.close();
		assertThat(viewer.isMediaLoaded()).isFalse();
	}

	@Test
	void doesNotThrowWhenClosingAfterLoadAttempts() throws Exception {
		Path invalid = Files.write(root.resolve("sample.mp4"), new byte[] { 1, 2, 3 });
		try (VideoViewer viewer = new VideoViewer(invalid)) {
			assertThat(viewer.isMediaLoaded()).isFalse();
		}
	}

	private Path copyResourceToTemp(String name) throws Exception {
		var url = getClass().getResource("/io/simpleide/media/" + name);
		assertThat(url).as("test resource %s must be present", name).isNotNull();
		Path source = Path.of(url.toURI());
		Path destination = root.resolve(name);
		Files.copy(source, destination);
		return destination;
	}

	private void probeMediaBackend(Path sample) throws Exception {
		JavaFxAudioOutput.configurePulseFallback();
		SwingUtilities.invokeAndWait(() -> new JFXPanel());
		CompletableFuture<Void> ready = new CompletableFuture<>();
		Platform.runLater(() -> {
			try {
				Media media = new Media(sample.toUri().toString());
				media.setOnError(() -> ready.completeExceptionally(media.getError()));
				MediaPlayer player = new MediaPlayer(media);
				player.setOnError(() -> ready.completeExceptionally(player.getError()));
				player.setOnReady(() -> {
					player.dispose();
					ready.complete(null);
				});
			}
			catch (Throwable error) {
				ready.completeExceptionally(error);
			}
		});
		ready.get(10, TimeUnit.SECONDS);
	}

}
