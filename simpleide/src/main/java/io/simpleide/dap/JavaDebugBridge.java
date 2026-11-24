package io.simpleide.dap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts Java debug sessions through the JDT language server.
 *
 * <p>
 * The Java debug adapter is not a standalone process: it is hosted by the JDT language
 * server and reached over TCP. The bridge asks the language server to start its debug
 * server with the {@code vscode.java.startDebugSession} command, resolves the main class
 * and class path with {@code vscode.java.resolveMainClass} and
 * {@code vscode.java.resolveClasspath}, and then connects a {@link DapSession} to the
 * returned port. This keeps Java debugging on the same language server that already
 * provides completion and diagnostics for the file.
 *
 * <p>
 * Covered by {@code JavaDebugBridgeTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class JavaDebugBridge {

	private static final Logger LOGGER = LoggerFactory.getLogger(JavaDebugBridge.class);

	/**
	 * Command that starts the DAP server hosted by the language server and returns its
	 * port.
	 */
	public static final String START_DEBUG_SESSION = "vscode.java.startDebugSession";

	/**
	 * Command that resolves the main classes of a source file.
	 */
	public static final String RESOLVE_MAIN_CLASS = "vscode.java.resolveMainClass";

	/**
	 * Command that resolves the module and class paths of a main class.
	 */
	public static final String RESOLVE_CLASSPATH = "vscode.java.resolveClasspath";

	private static final long COMMAND_TIMEOUT_SECONDS = 30;

	private JavaDebugBridge() {
	}

	/**
	 * Minimal LSP command surface used by the bridge.
	 *
	 * <p>
	 * {@link io.simpleide.lsp.client.LspClient} satisfies this contract through its
	 * {@code executeCommand} method; tests provide a stub.
	 *
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public interface JavaDebugClient {

		/**
		 * Runs a workspace command on the language server.
		 * @param command command identifier
		 * @param arguments command arguments
		 * @return a future completed with the command result
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		CompletableFuture<Object> executeCommand(String command, List<Object> arguments);

	}

	/**
	 * Starts a debug session for a Java source file.
	 * @param client language-server command client
	 * @param program Java source file to debug
	 * @param breakpoints initial one-based line breakpoints
	 * @param listener listener for execution state changes, may be {@code null}
	 * @return the started session
	 * @throws IOException when the language server cannot start the debug server or the
	 * session cannot connect
	 * @throws InterruptedException when the calling thread is interrupted
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static DapSession start(JavaDebugClient client, Path program, List<Integer> breakpoints,
			DapSession.Listener listener) throws IOException, InterruptedException {
		int port = startDebugSession(client);
		Map<String, Object> launchArguments = launchArguments(client, program);
		DapSession session = new DapSession(program, port, launchArguments, listener);
		session.start(breakpoints);
		return session;
	}

	/**
	 * Asks the language server to start its debug server and returns the port.
	 * @param client language-server command client
	 * @return the TCP port of the debug server
	 * @throws IOException when the command fails or returns no port
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static int startDebugSession(JavaDebugClient client) throws IOException, InterruptedException {
		Object result = invoke(client, START_DEBUG_SESSION, List.of());
		Integer port = asPort(result);
		if (port == null || port <= 0) {
			throw new IOException("The Java language server did not return a debug port.");
		}
		LOGGER.debug("JDT debug server is listening on port {}.", port);
		return port;
	}

	/**
	 * Builds the DAP launch properties for a Java source file.
	 * @param client language-server command client
	 * @param program Java source file
	 * @return the launch properties sent in the DAP {@code launch} request
	 * @throws IOException when the main class or class path cannot be resolved
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static Map<String, Object> launchArguments(JavaDebugClient client, Path program)
			throws IOException, InterruptedException {
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("type", "java");
		arguments.put("request", "launch");
		arguments.put("name", program.getFileName().toString());
		arguments.put("console", "internalConsole");
		arguments.put("noDebug", Boolean.FALSE);
		Path parent = program.getParent();
		if (parent != null) {
			arguments.put("cwd", parent.toString());
		}

		List<Map<String, Object>> mainClasses = resolveMainClasses(client, program);
		Map<String, Object> selected = selectMainClass(mainClasses, program.getFileName().toString());
		String mainClass = selected == null ? null : asString(selected.get("mainClass"));
		String projectName = selected == null ? null : asString(selected.get("projectName"));
		if (mainClass == null || mainClass.isBlank()) {
			throw new IOException(
					"The Java language server could not resolve a main class for " + program.getFileName() + ".");
		}
		arguments.put("mainClass", mainClass);
		if (projectName != null && !projectName.isBlank()) {
			arguments.put("projectName", projectName);
		}

		List<String> modulePaths = new ArrayList<>();
		List<String> classPaths = new ArrayList<>();
		Object classpathResult = invoke(client, RESOLVE_CLASSPATH,
				List.of(mainClass, projectName == null ? "" : projectName));
		if (classpathResult instanceof List<?> entries) {
			if (entries.size() > 0 && entries.get(0) instanceof List<?> modules) {
				for (Object module : modules) {
					if (module != null) {
						modulePaths.add(module.toString());
					}
				}
			}
			if (entries.size() > 1 && entries.get(1) instanceof List<?> paths) {
				for (Object path : paths) {
					if (path != null) {
						classPaths.add(path.toString());
					}
				}
			}
		}
		if (!modulePaths.isEmpty()) {
			arguments.put("modulePaths", modulePaths);
		}
		if (!classPaths.isEmpty()) {
			arguments.put("classPaths", classPaths);
		}
		return arguments;
	}

	private static List<Map<String, Object>> resolveMainClasses(JavaDebugClient client, Path program)
			throws IOException, InterruptedException {
		Object result = invoke(client, RESOLVE_MAIN_CLASS, List.of(List.of(program.toUri().toString())));
		List<Map<String, Object>> entries = new ArrayList<>();
		if (result instanceof List<?> list) {
			for (Object element : list) {
				if (element instanceof Map<?, ?> map) {
					Map<String, Object> entry = new LinkedHashMap<>();
					for (Map.Entry<?, ?> value : map.entrySet()) {
						if (value.getKey() != null) {
							entry.put(value.getKey().toString(), value.getValue());
						}
					}
					entries.add(entry);
				}
			}
		}
		return entries;
	}

	private static Map<String, Object> selectMainClass(List<Map<String, Object>> mainClasses, String fileName) {
		String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
		for (Map<String, Object> entry : mainClasses) {
			String mainClass = asString(entry.get("mainClass"));
			if (mainClass == null) {
				continue;
			}
			String simpleName = mainClass.contains(".") ? mainClass.substring(mainClass.lastIndexOf('.') + 1)
					: mainClass;
			if (simpleName.toLowerCase(Locale.ROOT).equals(baseName.toLowerCase(Locale.ROOT))) {
				return entry;
			}
		}
		return mainClasses.isEmpty() ? null : mainClasses.get(0);
	}

	private static Object invoke(JavaDebugClient client, String command, List<Object> arguments)
			throws IOException, InterruptedException {
		CompletableFuture<Object> future;
		try {
			future = client.executeCommand(command, arguments);
		}
		catch (RuntimeException error) {
			throw new IOException("The Java language server rejected the command " + command + ".", error);
		}
		try {
			return future.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw error;
		}
		catch (TimeoutException error) {
			throw new IOException("The Java language server did not answer the command " + command + " in time.",
					error);
		}
		catch (ExecutionException error) {
			Throwable cause = error.getCause() == null ? error : error.getCause();
			throw new IOException("The Java language server failed the command " + command + ": " + cause.getMessage(),
					cause);
		}
	}

	private static Integer asPort(Object result) {
		if (result instanceof Number number) {
			return number.intValue();
		}
		if (result instanceof Map<?, ?> map && map.get("port") instanceof Number number) {
			return number.intValue();
		}
		return null;
	}

	private static String asString(Object value) {
		return value == null ? null : value.toString();
	}

}