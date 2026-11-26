package io.simpleide.lsp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.dowload.LspManager;

/**
 * Shared setup for language-server integration tests.
 *
 * <p>
 * The harness starts the same command the IDE resolves for a language, waits for the LSP
 * handshake and answers a completion request against a real document. Tests skip when the
 * language runtime required by the server is missing or when the platform has no server
 * definition, following the convention used by the debug adapter integration tests.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
final class LspIntegrationHarness {

	private static final long STARTUP_TIMEOUT_SECONDS = 180;

	private LspIntegrationHarness() {
	}

	/**
	 * Skips the test when a runtime command required by the server is not installed.
	 * @param command command to probe
	 * @param arguments arguments passed to the command
	 */
	static void requireToolchain(String command, String... arguments) {
		assumeTrue(isAvailable(command, arguments),
				command + " is required by the language server but is not available");
	}

	/**
	 * Checks whether a command can be executed successfully.
	 * @param command command to probe
	 * @param arguments arguments passed to the command
	 * @return {@code true} when the command exits with status zero
	 */
	static boolean isAvailable(String command, String... arguments) {
		List<String> commandLine = new ArrayList<>();
		commandLine.add(command);
		commandLine.addAll(List.of(arguments));
		try {
			Process process = new ProcessBuilder(commandLine).redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
			return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
		}
		catch (IOException error) {
			return false;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Checks whether the {@code rust-src} component is installed.
	 *
	 * <p>
	 * Without it, rust-analyzer cannot load the standard library and answers every
	 * completion request with a {@code null} result, even for a Cargo project.
	 * @return {@code true} when the sysroot ships the standard library sources
	 */
	static boolean hasRustSources() {
		List<String> command = List.of("rustc", "--print", "sysroot");
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0 || output.isBlank()) {
				return false;
			}
			return Files.isDirectory(Paths.get(output, "lib", "rustlib", "src", "rust", "library"));
		}
		catch (IOException error) {
			return false;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Starts the language server resolved for a language and waits for initialization.
	 * @param languageId LSP language identifier
	 * @param workspace workspace root
	 * @param initializationOptions options sent in the {@code initialize} request
	 * @return the initialized client; the caller must close it
	 */
	static LspClient start(String languageId, Path workspace, Map<String, Object> initializationOptions) {
		Map<String, Object> options = new HashMap<>(LspManager.getInitializationOptions(languageId, workspace));
		options.putAll(initializationOptions);
		List<String> command;
		try {
			command = LspManager.getComandRun(languageId, workspace);
		}
		catch (RuntimeException error) {
			assumeTrue(false, "The " + languageId + " language server could not be installed: " + error.getMessage());
			return null;
		}
		assumeTrue(!command.isEmpty(), "No " + languageId + " language server is registered for this platform");

		LspClient client;
		try {
			client = new LspClient(command, workspace, null, Map.of(), null, options);
		}
		catch (IOException error) {
			assumeTrue(false, "The " + languageId + " language server could not be started: " + error.getMessage());
			return null;
		}
		try {
			client.getInitialized().get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			return client;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			client.close();
			throw new AssertionError("Interrupted while initializing the " + languageId + " language server", error);
		}
		catch (ExecutionException | TimeoutException error) {
			client.close();
			throw new AssertionError("The " + languageId + " language server did not initialize: " + error.getMessage(),
					error);
		}
	}

	/**
	 * Opens a document and checks that the server answers a completion request.
	 * @param client initialized client
	 * @param languageId LSP language identifier
	 * @param file opened file
	 * @param content file content
	 * @param position completion position
	 */
	static void assertAnswersCompletion(LspClient client, String languageId, Path file, String content,
			Position position) {
		String uri = file.toUri().toString();
		client.didOpen(new TextDocumentItem(uri, languageId, 1, content));
		CompletionParams params = new CompletionParams(new TextDocumentIdentifier(uri), position);

		await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
			try {
				Either<List<CompletionItem>, CompletionList> completion = client.completion(params)
					.get(30, TimeUnit.SECONDS);
				assertThat(completion).as("%s completion response", languageId).isNotNull();
				List<CompletionItem> items = completion.isLeft() ? completion.getLeft()
						: completion.getRight() == null ? null : completion.getRight().getItems();
				assertThat(items).as("%s completion items", languageId).isNotNull().isNotEmpty();
			}
			catch (ExecutionException error) {
				throw new AssertionError(
						"The " + languageId + " language server rejected the completion request: " + error.getMessage(),
						error);
			}
		});
	}

}
