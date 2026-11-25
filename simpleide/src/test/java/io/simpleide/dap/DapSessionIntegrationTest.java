package io.simpleide.dap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;

/**
 * Integration tests that start a real debug adapter process and drive a full debug
 * session over the Debug Adapter Protocol.
 *
 * <p>
 * The adapter is {@link FakeDapServer} launched as a child process, so the tests exercise
 * the real framing, the real sockets and pipes, the breakpoint handshake and every
 * execution control exposed by the IDE. The adapter echoes each control request it
 * receives, which lets the test assert that the command really travelled over the wire.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("debug")
class DapSessionIntegrationTest {

	private static final List<String> TRANSPORTS = List.of("stdio", "tcp");

	@TempDir
	Path tempDirectory;

	private final List<String> published = new CopyOnWriteArrayList<>();

	private final List<String> listenerEvents = new CopyOnWriteArrayList<>();

	private AutoCloseable outputSubscription;

	@BeforeEach
	void subscribeToOutput() throws InterruptedException {
		while (Input.getInstance().get(0) != null) {
			// Drain input left by other tests.
		}
		published.clear();
		listenerEvents.clear();
		outputSubscription = Output.getInstance()
			.addListener(line -> published.add("[" + line.type() + "] " + line.text()));
	}

	@AfterEach
	void unsubscribeFromOutput() throws Exception {
		outputSubscription.close();
	}

	@Test
	void completesHandshakeAndReportsBreakpointsOverStdio() throws Exception {
		runSession("stdio", List.of(1, 2));
	}

	@Test
	void completesHandshakeAndReportsBreakpointsOverTcp() throws Exception {
		runSession("tcp", List.of(7, 9));
	}

	private void runSession(String transport, List<Integer> lines) throws Exception {
		DapSession session = new DapSession(adapter(transport, true), tempDirectory, program(), listener());
		try {
			session.start(lines);

			assertThat(session.supportsConfigurationDone()).isTrue();
			awaitOutput("[OUTPUT] " + Output.INITIALIZED);
			awaitOutput("{BREAKPOINTS} " + join(lines));
			await().atMost(20, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(listenerEvents).contains("stopped:breakpoint", "terminated"));
			awaitOutput("[OUTPUT] " + Output.FINISHED);
		}
		finally {
			session.close();
		}
	}

	@Test
	void drivesEveryExecutionControlOverTheWire() throws Exception {
		DapSession session = new DapSession(adapter("stdio", false), tempDirectory, program(), listener());
		try {
			session.start(List.of(1));
			await().atMost(20, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(listenerEvents).contains("stopped:breakpoint"));

			session.stepOver();
			awaitOutput("{CONTROL} next");

			session.stepInto();
			awaitOutput("{CONTROL} stepIn");

			session.stepOut();
			awaitOutput("{CONTROL} stepOut");

			session.pause();
			awaitOutput("{CONTROL} pause");

			session.resume();
			awaitOutput("{CONTROL} continue");
			await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(listenerEvents).contains("continued"));
		}
		finally {
			session.close();
		}
	}

	@Test
	void sendsUpdatedBreakpointsWhileTheSessionIsRunning() throws Exception {
		DapSession session = new DapSession(adapter("tcp", false), tempDirectory, program(), listener());
		try {
			session.start(List.of(1));
			awaitOutput("{BREAKPOINTS} 1");

			session.setBreakpoints(program(), List.of(4, 8));
			awaitOutput("{BREAKPOINTS} 4,8");
		}
		finally {
			session.close();
		}
	}

	@Test
	void forwardsTypedInputToTheLiveDebuggee() throws Exception {
		DapSession session = new DapSession(adapterWithStdinEcho(), tempDirectory, program(), listener());
		try {
			session.start(List.of(1));

			awaitOutput("[OUTPUT] " + Output.WAITING_FOR_STDIN);

			Input.getInstance().set("hello from the test");
			awaitOutput("ECHO: hello from the test");

			Input.getInstance().set("quit");
			awaitOutput("ECHO: quit");
		}
		finally {
			session.close();
		}
		await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(listenerEvents).contains("terminated"));
		awaitOutput("[OUTPUT] " + Output.FINISHED);
	}

	@Test
	void reportsTheLaunchErrorInsteadOfWaitingForTheInitializedEvent() throws Exception {
		String message = "Failed to launch: Go version go1.22.2 is too old for this version of Delve";
		DapSession session = new DapSession(adapterFailingLaunch(message), tempDirectory, program(), listener());
		long start = System.nanoTime();
		try {
			assertThatThrownBy(() -> session.start(List.of(1))).isInstanceOf(IOException.class)
				.hasMessageContaining("rejected the launch request")
				.hasMessageContaining("too old for this version of Delve");
		}
		finally {
			session.close();
		}
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
		assertThat(elapsedMillis).as("milliseconds until the launch error surfaced").isLessThan(25_000);
	}

	@Test
	void reportsAnUnlaunchableAdapterAsIOException() throws Exception {
		DapAdapter adapter = new DapAdapter("missing", "Missing adapter", "1.0.0", "missing",
				List.of("this-command-does-not-exist"), List.of(), "stdio", Map.of(), Map.of());
		DapSession session = new DapSession(adapter, tempDirectory, program(), listener());
		try {
			assertThatThrownBy(() -> session.start(List.of())).isInstanceOf(IOException.class)
				.hasMessageContaining("missing");
		}
		finally {
			session.close();
		}
	}

	private void awaitOutput(String fragment) {
		await().atMost(20, TimeUnit.SECONDS)
			.untilAsserted(() -> assertThat(published).as("output lines").anyMatch(line -> line.contains(fragment)));
	}

	private DapAdapter adapter(String transport, boolean terminateAfterStop) {
		List<String> arguments = new ArrayList<>(List.of("-cp", classpath(), FakeDapServer.class.getName()));
		if (transport.equals("tcp")) {
			arguments.add("tcp");
			arguments.add("${port}");
		}
		if (terminateAfterStop) {
			arguments.add("--terminate-after-stop");
		}
		return new DapAdapter("fake", "Fake debug adapter", "1.0.0", "fake", javaExecutable(), arguments, List.of(),
				transport, Map.of(), Map.of());
	}

	private DapAdapter adapterFailingLaunch(String message) {
		return new DapAdapter("fake", "Fake debug adapter", "1.0.0", javaExecutable(),
				List.of("-cp", classpath(), FakeDapServer.class.getName(), "--fail-launch", message), List.of(),
				"stdio", Map.of(), Map.of());
	}

	private DapAdapter adapterWithStdinEcho() {
		return new DapAdapter("fake", "Fake debug adapter", "1.0.0", "fake", javaExecutable(),
				List.of("-cp", classpath(), FakeDapServer.class.getName(), "--stdin-echo", classpath()), List.of(),
				"stdio", Map.of(), Map.of());
	}

	private Path program() throws IOException {
		Path program = tempDirectory.resolve("program.txt");
		if (!Files.exists(program)) {
			Files.writeString(program, "line one\nline two\nline three\nline four\n");
		}
		return program;
	}

	private DapSession.Listener listener() {
		return new DapSession.Listener() {
			@Override
			public void onStopped(int threadId, String reason) {
				listenerEvents.add("stopped:" + reason);
			}

			@Override
			public void onContinued() {
				listenerEvents.add("continued");
			}

			@Override
			public void onTerminated() {
				listenerEvents.add("terminated");
			}
		};
	}

	private static String join(List<Integer> lines) {
		StringBuilder text = new StringBuilder();
		for (Integer line : lines) {
			if (text.length() > 0) {
				text.append(',');
			}
			text.append(line);
		}
		return text.toString();
	}

	private static String javaExecutable() {
		String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe"
				: "java";
		return Path.of(System.getProperty("java.home"), "bin", executable).toString();
	}

	private static String classpath() {
		return System.getProperty("java.class.path");
	}

}
