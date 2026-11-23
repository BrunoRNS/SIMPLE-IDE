package io.simpleide.lsp.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.lsp.client.LspClient;

class LanguageServerManagerTest {

	@TempDir
	Path tempDir;

	@Test
	void preparesLanguageServerConnectionsOffTheEdt() throws Exception {
		CountDownLatch preparationStarted = new CountDownLatch(1);
		CountDownLatch releasePreparation = new CountDownLatch(1);
		CountDownLatch edtHeartbeat = new CountDownLatch(1);
		AtomicReference<CompletableFuture<LspClient>> connection = new AtomicReference<>();
		LanguageServerManager manager = new LanguageServerManager(ignored -> {
		}, path -> {
			assertThat(Thread.currentThread().getName()).startsWith("simpleide-lsp-");
			preparationStarted.countDown();
			try {
				if (!releasePreparation.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out waiting to finish test preparation.");
				}
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Test preparation was interrupted.", error);
			}
			throw new IllegalStateException("Simulated workspace discovery failure.");
		}, ignored -> Map.of());

		try {
			SwingUtilities.invokeLater(() -> connection
				.set(manager.connect(new Document(tempDir.resolve("Main.java"), "java"), ignored -> {
				})));
			assertTrue(preparationStarted.await(5, TimeUnit.SECONDS));

			SwingUtilities.invokeLater(edtHeartbeat::countDown);

			assertTrue(edtHeartbeat.await(2, TimeUnit.SECONDS), "the EDT should remain responsive during setup");
			releasePreparation.countDown();
			assertTrue(connection.get() != null);
			Awaitility.await()
				.atMost(Duration.ofSeconds(5))
				.untilAsserted(() -> assertThat(connection.get().isCompletedExceptionally()).isTrue());
		}
		finally {
			releasePreparation.countDown();
			manager.close();
		}
	}

}
