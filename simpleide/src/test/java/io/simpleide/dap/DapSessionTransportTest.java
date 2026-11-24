package io.simpleide.dap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;

/**
 * Verifies that {@link DapSession} completes the DAP handshake and reports execution
 * state changes over the {@code stdio} and {@code tcp} transports.
 *
 * <p>
 * The tests launch {@link FakeDapServer} as a child process, so they exercise the real
 * protocol code without downloading a debug adapter.
 */
class DapSessionTransportTest {

	@TempDir
	Path tempDirectory;

	@Test
	void completesHandshakeOverStdio() throws Exception {
		List<String> events = new CopyOnWriteArrayList<>();
		DapSession session = new DapSession(stdioAdapter(), tempDirectory, program(), listener(events));
		try {
			session.start(List.of(1, 2));
			await().atMost(15, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(events).contains("stopped:breakpoint", "terminated"));
		}
		finally {
			session.close();
		}
	}

	@Test
	void completesHandshakeOverTcp() throws Exception {
		List<String> events = new CopyOnWriteArrayList<>();
		DapAdapter adapter = new DapAdapter("fake", "Fake", "1.0.0", "fake", javaExecutable(),
				List.of("-cp", classpath(), FakeDapServer.class.getName(), "tcp", "${port}"), List.of(), "tcp",
				java.util.Map.of(), java.util.Map.of());
		DapSession session = new DapSession(adapter, tempDirectory, program(), listener(events));
		try {
			session.start(List.of(1, 2));
			await().atMost(15, TimeUnit.SECONDS)
				.untilAsserted(() -> assertThat(events).contains("stopped:breakpoint", "terminated"));
		}
		finally {
			session.close();
		}
	}

	@Test
	void reportsAdapterFailuresAsIOException() throws Exception {
		DapAdapter adapter = new DapAdapter("missing", "Missing", "1.0.0", "missing",
				List.of("this-command-does-not-exist"), List.of(), "stdio", java.util.Map.of(), java.util.Map.of());
		DapSession session = new DapSession(adapter, tempDirectory, program(), listener(new CopyOnWriteArrayList<>()));
		try {
			assertThat(org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> session.start(List.of())))
				.hasMessageContaining("debug adapter");
		}
		finally {
			session.close();
		}
	}

	private DapAdapter stdioAdapter() {
		return new DapAdapter("fake", "Fake", "1.0.0", "fake", javaExecutable(),
				List.of("-cp", classpath(), FakeDapServer.class.getName()), List.of(), "stdio", java.util.Map.of(),
				java.util.Map.of());
	}

	private Path program() throws IOException {
		Path program = tempDirectory.resolve("program.txt");
		Files.writeString(program, "line one\nline two\n");
		return program;
	}

	private DapSession.Listener listener(List<String> events) {
		return new DapSession.Listener() {
			@Override
			public void onStopped(int threadId, String reason) {
				events.add("stopped:" + reason);
				Output.getInstance().publish(OutputType.DEBUG, "test stopped: " + reason);
			}

			@Override
			public void onTerminated() {
				events.add("terminated");
			}
		};
	}

	private static String javaExecutable() {
		return Path.of(System.getProperty("java.home"), "bin", "java").toString();
	}

	private static String classpath() {
		return System.getProperty("java.class.path");
	}

}