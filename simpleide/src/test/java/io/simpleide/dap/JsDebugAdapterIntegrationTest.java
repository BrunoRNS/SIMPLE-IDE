package io.simpleide.dap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.config.LangPlatform;
import io.simpleide.language.io.Output;

/**
 * Debugs a real Node.js program with the JavaScript adapter installed by the IDE.
 *
 * <p>
 * The adapter definition comes from the bundled registry and the session is started with
 * a real {@code js-debug} process speaking DAP over TCP. The test therefore needs the
 * {@code node} executable and the adapter downloaded into {@code ~/.simpleide/dap}, so it
 * is tagged {@code language-tools} and excluded by {@code make test-nolang}. It is
 * skipped instead of failing when the current operating system and architecture have no
 * registered adapter, when the adapter is not installed, or when {@code node} is missing,
 * which keeps headless CI images without the JavaScript toolchain green.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("language-tools")
@Tag("debug")
class JsDebugAdapterIntegrationTest {

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
	void stopsAtABreakpointAndStepsThroughARealNodeProgram() throws Exception {
		DapAdapter adapter = registeredAdapter();
		Path installDirectory = DapInstaller.getInstallDirectory(adapter);
		Path program = Files.writeString(tempDirectory.resolve("target.js"),
				"const value = 1;\nconst doubled = value * 2;\nconsole.log('doubled=' + doubled);\n");

		DapSession session = new DapSession(adapter, installDirectory, program, new DapSession.Listener() {
			@Override
			public void onStopped(int threadId, String reason) {
				events.add("stopped:" + reason);
			}

			@Override
			public void onTerminated() {
				events.add("terminated");
			}
		});
		try (AutoCloseable subscription = outputSubscription) {
			session.start(List.of(2));
			await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("stopped:breakpoint"));

			session.stepOver();
			await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("stopped:step"));

			session.resume();
			await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("terminated"));
			await().atMost(30, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(published).anyMatch(line -> line.contains("doubled=2")));
		}
		finally {
			session.close();
		}
	}

	private DapAdapter registeredAdapter() {
		DapAdapter adapter;
		try {
			adapter = DapRegistry.loadDefault().findForPlatform(LangPlatform.getCurrentPlatform(), "javascript");
		}
		catch (RuntimeException error) {
			assumeTrue(false, "The bundled debug adapter registry cannot be read: " + error.getMessage());
			return null;
		}
		assumeTrue(adapter != null,
				"No JavaScript debug adapter is registered for " + LangPlatform.getCurrentPlatform().getDisplayName());
		assumeTrue(DapInstaller.isInstalled(adapter), adapter.getName() + " " + adapter.getVersion()
				+ " is not installed under " + DapInstaller.getInstallDirectory(adapter));

		Path installDirectory = DapInstaller.getInstallDirectory(adapter);
		Path server = Path.of(adapter.getArguments().get(0).replace("${installDir}", installDirectory.toString()));
		assumeTrue(Files.isRegularFile(server), "The JavaScript debug server is missing: " + server);
		assumeTrue(isNodeAvailable(), "node is not available on PATH");
		return adapter;
	}

	private static boolean isNodeAvailable() {
		try {
			Process process = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
			return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
		}
		catch (Exception error) {
			return false;
		}
	}

}
