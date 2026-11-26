package io.simpleide.lsp.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.simpleide.lsp.client.LspClient;

/**
 * Waits for a language server client until a shared shutdown deadline.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LspClientAwaiter {

	private LspClientAwaiter() {
	}

	static LspClient await(CompletableFuture<LspClient> client, long deadline) {
		long remaining = deadline - System.nanoTime();
		if (remaining <= 0) {
			return completedClient(client);
		}
		try {
			return client.get(remaining, TimeUnit.NANOSECONDS);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return completedClient(client);
		}
		catch (CancellationException | ExecutionException | TimeoutException error) {
			return completedClient(client);
		}
	}

	private static LspClient completedClient(CompletableFuture<LspClient> client) {
		if (!client.isDone() || client.isCompletedExceptionally() || client.isCancelled()) {
			return null;
		}
		try {
			return client.getNow(null);
		}
		catch (RuntimeException error) {
			return null;
		}
	}

}
