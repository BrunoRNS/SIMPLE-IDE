package io.simpleide.dap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.language.io.Output;
import io.simpleide.lsp.client.LspClient;
import io.simpleide.lsp.dowload.LspManager;

/**
 * Debugs a real Java program through Eclipse JDT LS and the {@code vscode-java-debug}
 * plugin downloaded at runtime.
 *
 * <p>
 * The test starts the same language-server command the IDE uses, installs the newest
 * {@code vscode-java-debug} release compatible with the running JDK (see
 * {@link JavaDebugInstaller}), passes its bundle in the JDT LS
 * {@code initializationOptions}, asks the server to start a debug session and drives a
 * real launch through {@link JavaDebugBridge}. It is tagged {@code language-tools} and is
 * skipped when the platform cannot run JDT LS, when Java is older than 17, or when the
 * language server or adapter cannot be installed.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("language-tools")
@Tag("debug")
class JavaRealDebugIntegrationTest {

	@TempDir
	Path tempDirectory;

	private final List<String> published = new CopyOnWriteArrayList<>();

	private final List<String> events = new CopyOnWriteArrayList<>();

	private AutoCloseable outputSubscription;

	@BeforeEach
	void subscribeToOutput() {
		published.clear();
		events.clear();
		outputSubscription = Output.getInstance().addListener(line -> published.add(line.text()));
	}

	@Test
	void stopsAtABreakpointAndFinishesARealJavaProgram() throws Exception {
		assumeTrue(Boolean.getBoolean("simpleide.java.debug.real"),
				"Real Java debug through jdtls is opt-in; run with -Dsimpleide.java.debug.real to enable");
		assumeTrue(Runtime.version().feature() >= 17, "JDT LS requires Java 17 or newer");
		DapAdapter adapter = DapRegistry.loadDefault().findForPlatform(LangPlatform.getCurrentPlatform(), "java");
		assumeTrue(adapter != null,
				"No Java debug adapter is registered for " + LangPlatform.getCurrentPlatform().getDisplayName());

		String source = """
				public class Main {
				    public static void main(String[] args) {
				        int value = 1;
				        int doubled = value * 2;
				        System.out.println("doubled=" + doubled);
				    }
				}
				""";
		Path program = Files.writeString(tempDirectory.resolve("Main.java"), source);

		List<String> command;
		Path bundle;
		try {
			command = LspManager.getComandRun("java", tempDirectory);
			bundle = JavaDebugInstaller.install();
		}
		catch (RuntimeException | java.io.IOException error) {
			assumeTrue(false,
					"The Java language server or debug adapter could not be installed: " + error.getMessage());
			return;
		}
		assumeTrue(!command.isEmpty(), "No Java language server is installed for this platform");

		LspClient client = new LspClient(command, tempDirectory, null, Map.of(), null,
				Map.of("bundles", List.of(bundle.toString())));
		try (AutoCloseable subscription = outputSubscription) {
			try {
				client.getInitialized().get(90, TimeUnit.SECONDS);
			}
			catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
				assumeTrue(false, "The Java language server did not initialize: " + error.getMessage());
				return;
			}
			client.didOpen(new TextDocumentItem(program.toUri().toString(), "java", 1, source));

			JavaDebugBridge.JavaDebugClient bridgeClient = (commandId, arguments) -> client.executeCommand(commandId,
					arguments);
			DapSession session = JavaDebugBridge.start(bridgeClient, program, List.of(4), new DapSession.Listener() {
				@Override
				public void onStopped(int threadId, String reason) {
					events.add("stopped:" + reason);
				}

				@Override
				public void onTerminated() {
					events.add("terminated");
				}
			});
			try {
				await().atMost(120, TimeUnit.SECONDS)
					.untilAsserted(() -> assertThat(events).contains("stopped:breakpoint"));
				session.resume();
				await().atMost(120, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("terminated"));
				await().atMost(60, TimeUnit.SECONDS)
					.untilAsserted(() -> assertThat(published).anyMatch(line -> line.contains("doubled=2")));
			}
			finally {
				session.close();
			}
		}
		finally {
			client.close();
		}
	}

}
