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
 * Debugs a real Python program with the debugpy adapter installed by the IDE.
 *
 * <p>
 * The adapter is resolved from the bundled registry, installed on first use and started
 * as a real debugpy process over TCP. The test is tagged {@code language-tools} and is
 * skipped when the platform has no registered adapter, when the disk cache is
 * unavailable, or when the local Python is older than the interpreter supported by the
 * bundled debugpy (Debian 10 ships Python 3.7, while debugpy requires 3.8 or newer).
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("language-tools")
@Tag("debug")
class PythonDebugAdapterIntegrationTest {

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
	void stopsAtABreakpointAndFinishesARealPythonProgram() throws Exception {
		DapAdapter adapter = registeredAdapter();
		int pythonFeature = pythonFeature();
		assumeTrue(pythonFeature >= 8, "debugpy requires Python 3.8 or newer; detected Python 3." + pythonFeature
				+ ". Debian 10 ships Python 3.7, so install a newer interpreter or use Debian 11+.");
		Path program = Files.writeString(tempDirectory.resolve("target.py"),
				"value = 1\ndoubled = value * 2\nprint(f'doubled={doubled}')\n");
		DapManager manager = new DapManager(DapRegistry.loadDefault(), LangPlatform.getCurrentPlatform());
		DapManager.PreparedAdapter prepared;
		try {
			prepared = manager.prepare(program);
		}
		catch (DapManager.DapUnavailableException error) {
			assumeTrue(false, "The debugpy adapter could not be installed: " + error.getMessage());
			return;
		}
		assertThat(prepared.adapter().getId()).isEqualTo(adapter.getId());

		DapSession session = new DapSession(prepared.adapter(), prepared.installDirectory(), program,
				new DapSession.Listener() {
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
			await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("stopped:breakpoint"));

			session.resume();
			await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("terminated"));
			await().atMost(60, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(published).anyMatch(line -> line.contains("doubled=2")));
		}
		finally {
			session.close();
		}
	}

	private DapAdapter registeredAdapter() {
		DapAdapter adapter = DapRegistry.loadDefault().findForPlatform(LangPlatform.getCurrentPlatform(), "python");
		assumeTrue(adapter != null,
				"No Python debug adapter is registered for " + LangPlatform.getCurrentPlatform().getDisplayName());
		return adapter;
	}

	private static int pythonFeature() {
		try {
			Process process = new ProcessBuilder("python3", "-c", "import sys; print(sys.version_info[1])")
				.redirectErrorStream(true)
				.start();
			String output = new String(process.getInputStream().readAllBytes()).trim();
			if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
				assumeTrue(false, "python3 is not available on PATH");
			}
			return Integer.parseInt(output.lines().findFirst().orElse("0"));
		}
		catch (Exception error) {
			assumeTrue(false, "python3 is not available on PATH: " + error.getMessage());
			return 0;
		}
	}

}
