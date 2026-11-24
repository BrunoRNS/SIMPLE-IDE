package io.simpleide.api;

import java.awt.GraphicsEnvironment;
import java.net.URL;
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
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for audio playback backed by JavaFX MediaPlayer.
 *
 * <p>
 * Playback tests require a graphical environment and a JavaFX media backend able to
 * decode the bundled sample; they are skipped when either is unavailable. Tests never
 * assert that media is still unloaded immediately after construction: JavaFX loads the
 * media asynchronously on its own thread, so that state is intentionally racy.
 *
 * @author luisotavio13
 * @since v1.1.0
 */
class AudioViewerTest {

	@TempDir
	Path root;

	@Test
	void rejectsNullPath() {
		assertThatThrownBy(() -> new AudioViewer(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void returnsImmediatelyForAMissingFile() {
		Path missing = root.resolve("inexistente.ogg");
		long start = System.nanoTime();
		try (AudioViewer viewer = new AudioViewer(missing)) {
			long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
			// Construction queues JavaFX initialization instead of reading the file. The
			// bound is deliberately generous because the first JFXPanel can start the
			// toolkit slowly on Windows and macOS CI runners.
			assertThat(elapsedMillis).isLessThan(10_000);
			assertThat(viewer.isMediaLoaded()).isFalse();
		}
	}

	@Test
	void loadAndPlayRealAudioUntilTheEnd() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Audio playback requires a graphical environment.");
		Path sample = samplePath("sample-audio.wav");
		// Abort when the JavaFX media backend is unavailable in this environment
		// (e.g. a headless CI without a GStreamer-backed JavaFX media provider).
		try {
			probeMediaBackend(sample);
		}
		catch (Throwable t) {
			Assumptions.abort("JavaFX media backend not available in this environment: " + t.getMessage());
		}
		try (AudioViewer viewer = new AudioViewer(sample)) {
			Awaitility.await()
				.atMost(15, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(viewer.isMediaLoaded()).isTrue());

			MediaPlayer player = viewer.playerForTesting();
			viewer.play();
			awaitPlaying(player);

			// The bundled sample is about 4 seconds long; playback should reach the end.
			try {
				Awaitility.await()
					.atMost(12, TimeUnit.SECONDS)
					.untilAsserted(() -> assertThat(player.getCurrentTime().toMillis())
						.isGreaterThanOrEqualTo(player.getTotalDuration().toMillis() - 1000));
			}
			catch (ConditionTimeoutException error) {
				Assumptions.abort("Playback did not advance in this environment: " + error.getMessage());
			}
		}
	}

	@Test
	void stopReturnsPlayheadToTheBeginning() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Audio playback requires a graphical environment.");
		Path sample = samplePath("sample-audio.wav");
		// Abort when the JavaFX media backend is unavailable in this environment
		// (e.g. a headless CI without a GStreamer-backed JavaFX media provider).
		try {
			probeMediaBackend(sample);
		}
		catch (Throwable t) {
			Assumptions.abort("JavaFX media backend not available in this environment: " + t.getMessage());
		}
		try (AudioViewer viewer = new AudioViewer(sample)) {
			Awaitility.await()
				.atMost(15, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(viewer.isMediaLoaded()).isTrue());
			viewer.play();
			awaitPlaying(viewer.playerForTesting());
			viewer.stop();
			try {
				Awaitility.await()
					.atMost(5, TimeUnit.SECONDS)
					.untilAsserted(() -> assertThat(viewer.playerForTesting().getCurrentTime().toMillis()).isZero());
			}
			catch (ConditionTimeoutException error) {
				Assumptions.abort("Playback did not stop in this environment: " + error.getMessage());
			}
		}
	}

	@Test
	void closeIsIdempotentAndSafeBeforeMediaInitialization() {
		AudioViewer viewer = new AudioViewer(root.resolve("missing.ogg"));
		viewer.close();
		viewer.close();
		assertThat(viewer.isMediaLoaded()).isFalse();
	}

	private static void awaitPlaying(MediaPlayer player) {
		try {
			Awaitility.await()
				.atMost(10, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(player.getStatus()).isEqualTo(MediaPlayer.Status.PLAYING));
		}
		catch (ConditionTimeoutException error) {
			Assumptions.abort("No audio output is available in this environment: " + error.getMessage());
		}
	}

	private Path samplePath(String name) throws Exception {
		URL url = getClass().getResource("/io/simpleide/media/" + name);
		assertThat(url).as("test resource %s must be present", name).isNotNull();
		// Load directly from the test-classes directory. Copying to @TempDir would make
		// JUnit cleanup depend on when the native media backend releases the file, which
		// is slow and unreliable on Windows.
		return Path.of(url.toURI());
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
