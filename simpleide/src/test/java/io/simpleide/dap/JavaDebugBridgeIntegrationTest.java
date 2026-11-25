package io.simpleide.dap;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

import io.simpleide.language.io.Output;

/**
 * Integration tests for the Java debug path that runs through the JDT language server.
 *
 * <p>
 * The tests stand in for the language server with a stub client that answers the same
 * workspace commands {@code jdtls} answers, but the debug adapter itself is a real
 * {@link FakeDapServer} process reached over TCP. This exercises the real handshake,
 * launch arguments and execution controls of the Java path without requiring a JDT
 * language server installation in the build environment.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
@Tag("debug")
class JavaDebugBridgeIntegrationTest {

	@TempDir
	Path tempDirectory;

	private final List<String> published = new CopyOnWriteArrayList<>();

	private AutoCloseable outputSubscription;

	private Process adapterProcess;

	@BeforeEach
	void subscribeToOutput() {
		published.clear();
		outputSubscription = Output.getInstance()
			.addListener(line -> published.add("[" + line.type() + "] " + line.text()));
	}

	@AfterEach
	void cleanUp() throws Exception {
		outputSubscription.close();
		if (adapterProcess != null) {
			adapterProcess.destroyForcibly();
			adapterProcess = null;
		}
	}

	@Test
	void startsAJavaDebugSessionAndDrivesIt() throws Exception {
		int port = freePort();
		adapterProcess = startAdapter(port);
		StubJavaClient client = new StubJavaClient(port);
		List<String> events = new CopyOnWriteArrayList<>();

		DapSession session = JavaDebugBridge.start(client, program(), List.of(3), new DapSession.Listener() {
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
			assertThat(client.commands).containsExactly(JavaDebugBridge.START_DEBUG_SESSION,
					JavaDebugBridge.RESOLVE_MAIN_CLASS, JavaDebugBridge.RESOLVE_CLASSPATH);
			assertThat(client.commandArguments.get(1)).containsExactly(program().toUri().toString());
			assertThat(client.commandArguments.get(2)).containsExactly("com.example.App", "demo");

			awaitOutput("[OUTPUT] " + Output.INITIALIZED);
			awaitOutput("{LAUNCH} mainClass=com.example.App projectName=demo");
			awaitOutput("{BREAKPOINTS} 3");
			await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(events).contains("stopped:breakpoint"));

			session.stepOver();
			awaitOutput("{CONTROL} next");

			session.stepInto();
			awaitOutput("{CONTROL} stepIn");

			session.stepOut();
			awaitOutput("{CONTROL} stepOut");

			session.resume();
			awaitOutput("{CONTROL} continue");
		}
		finally {
			session.close();
		}
	}

	@Test
	void failsWhenTheLanguageServerReturnsNoDebugPort() throws Exception {
		StubJavaClient client = new StubJavaClient(0);

		assertThatThrownBy(() -> JavaDebugBridge.start(client, program(), List.of(), null))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("did not return a debug port");
		assertThat(client.commands).containsExactly(JavaDebugBridge.START_DEBUG_SESSION);
	}

	@Test
	void failsWhenNoMainClassCanBeResolved() throws Exception {
		StubJavaClient client = new StubJavaClient(1234);
		client.mainClasses = List.of();

		assertThatThrownBy(() -> JavaDebugBridge.launchArguments(client, program())).isInstanceOf(IOException.class)
			.hasMessageContaining("could not resolve a main class");
	}

	@Test
	void failsWhenTheLanguageServerRejectsTheCommand() throws Exception {
		JavaDebugBridge.JavaDebugClient client = (command, arguments) -> {
			throw new IllegalStateException("command not registered");
		};

		assertThatThrownBy(() -> JavaDebugBridge.startDebugSession(client)).isInstanceOf(IOException.class)
			.hasMessageContaining("rejected the command");
	}

	private void awaitOutput(String fragment) {
		await().atMost(20, TimeUnit.SECONDS)
			.untilAsserted(() -> assertThat(published).as("output lines").anyMatch(line -> line.contains(fragment)));
	}

	private Path program() throws IOException {
		Path program = tempDirectory.resolve("App.java");
		if (!Files.exists(program)) {
			Files.writeString(program, "package com.example;\n\npublic class App {\n}\n");
		}
		return program;
	}

	private Process startAdapter(int port) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(javaExecutable(), "-cp", classpath(), FakeDapServer.class.getName(),
				"tcp", Integer.toString(port));
		builder.redirectErrorStream(true);
		return builder.start();
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static String javaExecutable() {
		String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe"
				: "java";
		return Path.of(System.getProperty("java.home"), "bin", executable).toString();
	}

	private static String classpath() {
		return System.getProperty("java.class.path");
	}

	/**
	 * Language server stub that answers the commands issued by {@link JavaDebugBridge}.
	 */
	private static final class StubJavaClient implements JavaDebugBridge.JavaDebugClient {

		private final int port;

		private final List<String> commands = new CopyOnWriteArrayList<>();

		private final List<List<Object>> commandArguments = new CopyOnWriteArrayList<>();

		private List<Map<String, Object>> mainClasses = List
			.of(Map.of("mainClass", "com.example.App", "projectName", "demo"));

		private StubJavaClient(int port) {
			this.port = port;
		}

		@Override
		public CompletableFuture<Object> executeCommand(String command, List<Object> arguments) {
			commands.add(command);
			commandArguments.add(arguments);
			return switch (command) {
				case JavaDebugBridge.START_DEBUG_SESSION -> CompletableFuture.completedFuture(port);
				case JavaDebugBridge.RESOLVE_MAIN_CLASS -> CompletableFuture.completedFuture(mainClasses);
				case JavaDebugBridge.RESOLVE_CLASSPATH ->
					CompletableFuture.completedFuture(List.of(List.of(), List.of("/workspace/classes")));
				default -> CompletableFuture.completedFuture(null);
			};
		}

	}

}
