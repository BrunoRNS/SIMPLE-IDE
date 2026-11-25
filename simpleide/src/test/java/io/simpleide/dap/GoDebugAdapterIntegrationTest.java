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
 * Debugs a real Go program with the delve adapter installed by the IDE.
 *
 * <p>
 * The adapter is resolved from the bundled registry, installed on first use and started
 * as a real {@code dlv dap} process over TCP. The test is tagged {@code language-tools}
 * and is skipped when the platform has no registered delve download, when the local Go
 * toolchain is missing, or when the adapter cannot be installed.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("language-tools")
@Tag("debug")
class GoDebugAdapterIntegrationTest {

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
	void stopsAtABreakpointAndFinishesARealGoProgram() throws Exception {
		DapAdapter adapter = registeredAdapter();
		assumeTrue(commandAvailable("go"), "go is not available on PATH");
		assumeTrue(goVersionAtLeast(25), "delve 1.27.2 requires Go 1.25 or newer; detected " + goVersion());
		Files.writeString(tempDirectory.resolve("go.mod"), "module debugdemo\n\ngo 1.21\n");
		Path program = Files.writeString(tempDirectory.resolve("main.go"), """
				package main

				import "fmt"

				func main() {
					value := 1
					doubled := value * 2
					fmt.Printf("doubled=%d\\n", doubled)
				}
				""");
		DapManager manager = new DapManager(DapRegistry.loadDefault(), LangPlatform.getCurrentPlatform());
		DapManager.PreparedAdapter prepared;
		try {
			prepared = manager.prepare(program);
		}
		catch (DapManager.DapUnavailableException error) {
			assumeTrue(false, "The delve adapter could not be installed: " + error.getMessage());
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
			session.start(List.of(7));
			await().atMost(90, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("stopped:breakpoint"));

			session.resume();
			await().atMost(90, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("terminated"));
			await().atMost(60, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(published).anyMatch(line -> line.contains("doubled=2")));
		}
		finally {
			session.close();
		}
	}

	private DapAdapter registeredAdapter() {
		DapAdapter adapter = DapRegistry.loadDefault().findForPlatform(LangPlatform.getCurrentPlatform(), "go");
		assumeTrue(adapter != null,
				"No Go debug adapter is registered for " + LangPlatform.getCurrentPlatform().getDisplayName());
		return adapter;
	}

	private static boolean commandAvailable(String command) {
		try {
			Process process = new ProcessBuilder(command, "version").redirectErrorStream(true).start();
			return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
		}
		catch (Exception error) {
			return false;
		}
	}

	private static String goVersion() {
		try {
			Process process = new ProcessBuilder("go", "version").redirectErrorStream(true).start();
			java.io.InputStream input = process.getInputStream();
			byte[] buffer = new byte[256];
			int read = input.read(buffer);
			process.waitFor(10, TimeUnit.SECONDS);
			if (read <= 0) {
				return "unknown";
			}
			String output = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8);
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("go version go(\\d+)\\.(\\d+)")
				.matcher(output);
			if (matcher.find()) {
				return matcher.group(1) + "." + matcher.group(2);
			}
			return output.trim();
		}
		catch (Exception error) {
			return "unknown";
		}
	}

	private static boolean goVersionAtLeast(int minor) {
		String version = goVersion();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("go version go(\\d+)\\.(\\d+)")
			.matcher(version);
		if (!matcher.find()) {
			return false;
		}
		return Integer.parseInt(matcher.group(2)) >= minor;
	}

}
