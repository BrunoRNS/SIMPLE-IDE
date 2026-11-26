package io.simpleide.api;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.lsp4j.PublishDiagnosticsParams;

import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.core.Document;
import io.simpleide.lsp.core.LanguageServerManager;
import io.simpleide.lsp.dowload.LspManager;

/**
 * Facade for connecting source files to the language servers bundled with the IDE.
 *
 * <p>
 * The facade owns one shared {@link LanguageServerManager}, so callers that open several
 * files of the same language and workspace share a single server process. Servers are
 * installed on first use by {@link LspManager}, diagnostics are delivered to the handler
 * registered for each file, and {@link #shutdown()} stops every server started through
 * the facade.
 *
 * <p>
 * Statements are safe to call from any thread; the connection itself is completed
 * asynchronously by the language-server manager.
 *
 * @author BrunoRNS
 * @since v1.1.0
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public final class LanguageServers {

	private static LanguageServerManager sharedManager;

	private LanguageServers() {
	}

	/**
	 * Returns the LSP language identifier used for a file.
	 * @param file source file
	 * @return the language identifier, or {@code plaintext} when the file has no language
	 * server mapping
	 */
	public static String languageIdOf(Path file) {
		return switch (Editors.typeOf(Objects.requireNonNull(file, "file"))) {
			case JAVA -> "java";
			case CSHARP -> "csharp";
			case KOTLIN -> "kotlin";
			case GROOVY -> "groovy";
			case JAVASCRIPT -> "javascript";
			case GO -> "go";
			case RUST -> "rust";
			case PYTHON -> "python";
			case C -> "c";
			case CPP -> "cpp";
			default -> "plaintext";
		};
	}

	/**
	 * Returns whether the registry has a language server for a file on this platform.
	 *
	 * <p>
	 * Only the registry and the host platform are checked; the server is not installed
	 * and the runtime version is not probed. A {@code true} result therefore means the
	 * server is available in principle, not that it can start on this machine.
	 * @param file source file
	 * @return {@code true} when a matching server definition exists
	 */
	public static boolean isSupported(Path file) {
		return isSupported(languageIdOf(file));
	}

	/**
	 * Returns whether the registry has a language server for a language id.
	 * @param languageId LSP language identifier such as {@code python}
	 * @return {@code true} when a server definition supports this platform
	 */
	public static boolean isSupported(String languageId) {
		if (languageId == null || languageId.isBlank() || "plaintext".equals(languageId)) {
			return false;
		}
		try {
			return LspManager.getRegistry()
				.getServers()
				.stream()
				.anyMatch(server -> languageId.equals(server.getLanguageId()) && server.isSupportedOnCurrentPlatform());
		}
		catch (RuntimeException error) {
			return false;
		}
	}

	/**
	 * Connects a file to its shared language server, starting and installing it when
	 * necessary.
	 *
	 * <p>
	 * The returned future completes with the initialized {@link LspClient}. The client is
	 * owned by the facade and stays connected until {@link #disconnect(Path)} or
	 * {@link #shutdown()} is called. Documents are opened by the caller with
	 * {@link LspClient#didOpen(org.eclipse.lsp4j.TextDocumentItem)}.
	 * @param file source file
	 * @param diagnosticsHandler consumer for diagnostics published for this file, may be
	 * {@code null}
	 * @return a future completed with the connected client, or failed when no server is
	 * available or startup fails
	 */
	public static CompletableFuture<LspClient> connect(Path file,
			Consumer<PublishDiagnosticsParams> diagnosticsHandler) {
		Objects.requireNonNull(file, "file");
		Consumer<PublishDiagnosticsParams> handler = diagnosticsHandler == null ? ignored -> {
		} : diagnosticsHandler;
		String languageId = languageIdOf(file);
		if (!isSupported(languageId)) {
			return CompletableFuture.failedFuture(
					new IllegalStateException("No " + languageId + " language server is available on this platform."));
		}
		Threads.master();
		LanguageServerManager manager = manager();
		return manager.connect(new Document(file, languageId), handler);
	}

	/**
	 * Removes the diagnostics handler registered for a file.
	 * @param file file to disconnect
	 */
	public static void disconnect(Path file) {
		Objects.requireNonNull(file, "file");
		LanguageServerManager manager;
		synchronized (LanguageServers.class) {
			manager = sharedManager;
		}
		if (manager != null) {
			manager.disconnect(new Document(file, languageIdOf(file)));
		}
	}

	/**
	 * Stops every language server started through the facade and unregisters its threads.
	 */
	public static void shutdown() {
		LanguageServerManager manager;
		synchronized (LanguageServers.class) {
			manager = sharedManager;
			sharedManager = null;
		}
		if (manager != null) {
			manager.close();
		}
	}

	private static synchronized LanguageServerManager manager() {
		if (sharedManager == null) {
			sharedManager = new LanguageServerManager();
		}
		return sharedManager;
	}

}
