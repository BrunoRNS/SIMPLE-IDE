package io.simpleide.dap;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Scripted Debug Adapter Protocol server used by the DAP integration tests.
 *
 * <p>
 * The server speaks real DAP framing over standard input/output or TCP and behaves like a
 * small but complete debug adapter: it completes the {@code initialize} handshake,
 * answers {@code launch}, records {@code setBreakpoints}, reports a {@code stopped} event
 * after {@code configurationDone}, and answers every execution control request
 * ({@code continue}, {@code next}, {@code stepIn}, {@code stepOut}, {@code pause},
 * {@code threads} and {@code stackTrace}) with the events a real adapter emits.
 *
 * <p>
 * Every control request is echoed as a {@code stdout} output event ({@code {CONTROL}
 * next} and so on), so tests can observe from the IDE output queue that the adapter
 * really received the command. The same technique reports the breakpoint set the client
 * sent. When started with {@code --stdin-echo <classpath>} the adapter asks the client to
 * start {@link EchoStdin} through {@code runInTerminal}, which lets tests exercise real
 * standard input forwarding against a live debuggee process.
 *
 * <p>
 * It is launched as a child process, which keeps the tests independent of any external
 * debug adapter.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class FakeDapServer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final InputStream input;

	private final OutputStream output;

	private final String echoClasspath;

	private final boolean terminateAfterStop;

	/**
	 * Message used to reject the launch request instead of starting a session, or
	 * {@code null} when the adapter behaves normally.
	 */
	private final String launchFailure;

	private final List<Integer> breakpoints = new ArrayList<>();

	private int sequence;

	private int currentLine = 1;

	private boolean terminatedSent;

	FakeDapServer(InputStream input, OutputStream output, String echoClasspath, boolean terminateAfterStop) {
		this(input, output, echoClasspath, terminateAfterStop, null);
	}

	FakeDapServer(InputStream input, OutputStream output, String echoClasspath, boolean terminateAfterStop,
			String launchFailure) {
		this.input = new BufferedInputStream(input);
		this.output = output;
		this.echoClasspath = echoClasspath;
		this.terminateAfterStop = terminateAfterStop;
		this.launchFailure = launchFailure;
	}

	/**
	 * Runs the fake adapter.
	 * @param args {@code tcp <port>} for a TCP server (or no arguments for stdio), plus
	 * optional {@code --stdin-echo <classpath>} and {@code --fail-launch <message>}
	 * @throws IOException when the transport cannot be opened
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static void main(String[] args) throws IOException {
		int port = 0;
		String echoClasspath = null;
		String launchFailure = null;
		boolean tcp = false;
		boolean terminateAfterStop = false;
		for (int index = 0; index < args.length; index++) {
			switch (args[index]) {
				case "tcp" -> {
					tcp = true;
					if (index + 1 < args.length) {
						port = Integer.parseInt(args[++index]);
					}
				}
				case "--stdin-echo" -> {
					if (index + 1 < args.length) {
						echoClasspath = args[++index];
					}
				}
				case "--fail-launch" -> {
					if (index + 1 < args.length) {
						launchFailure = args[++index];
					}
				}
				case "--terminate-after-stop" -> terminateAfterStop = true;
				default -> {
					// Unknown arguments are ignored so the adapter stays launchable from
					// registry templates that append extra options.
				}
			}
		}
		if (!tcp) {
			new FakeDapServer(System.in, System.out, echoClasspath, terminateAfterStop, launchFailure).serve();
			return;
		}
		try (ServerSocket serverSocket = new ServerSocket(port); Socket socket = serverSocket.accept()) {
			new FakeDapServer(socket.getInputStream(), socket.getOutputStream(), echoClasspath, terminateAfterStop,
					launchFailure)
				.serve();
		}
	}

	private void serve() throws IOException {
		JsonNode message;
		while ((message = readMessage()) != null) {
			if ("request".equals(message.path("type").asText())) {
				handleRequest(message);
			}
			// Responses to requests issued by the client (runInTerminal) need no answer.
		}
		output.flush();
	}

	private void handleRequest(JsonNode message) throws IOException {
		String command = message.path("command").asText();
		int requestSequence = message.path("seq").asInt();
		switch (command) {
			case "initialize" -> {
				ObjectNode body = MAPPER.createObjectNode();
				body.put("supportsConfigurationDoneRequest", true);
				body.put("supportsTerminateRequest", true);
				body.put("supportsRunInTerminalRequest", true);
				respond(requestSequence, command, body);
				if (launchFailure == null) {
					event("initialized", MAPPER.createObjectNode());
				}
			}
			case "launch" -> {
				if (launchFailure != null) {
					// Adapters such as delve reject the launch before the debugger
					// exists,
					// so no initialized event and no session are ever created.
					respondError(requestSequence, command, launchFailure);
					return;
				}
				respond(requestSequence, command, null);
				JsonNode arguments = message.path("arguments");
				if (arguments.hasNonNull("mainClass")) {
					publish("{LAUNCH} mainClass=" + arguments.path("mainClass").asText() + " projectName="
							+ arguments.path("projectName").asText());
				}
				if (echoClasspath != null) {
					requestRunInTerminal();
				}
			}
			case "setBreakpoints" -> {
				breakpoints.clear();
				JsonNode requested = message.path("arguments").path("breakpoints");
				ArrayNode verified = MAPPER.createArrayNode();
				int id = 1;
				for (JsonNode breakpoint : requested) {
					int line = breakpoint.path("line").asInt();
					breakpoints.add(line);
					ObjectNode entry = MAPPER.createObjectNode();
					entry.put("id", id++);
					entry.put("verified", true);
					entry.put("line", line);
					verified.add(entry);
				}
				ObjectNode body = MAPPER.createObjectNode();
				body.set("breakpoints", verified);
				respond(requestSequence, command, body);
				publish("{BREAKPOINTS} " + joinedBreakpoints());
			}
			case "configurationDone" -> {
				respond(requestSequence, command, null);
				publish("{CONFIGURED}");
				stopped("breakpoint");
				if (terminateAfterStop) {
					terminated();
				}
			}
			case "continue" -> {
				ObjectNode body = MAPPER.createObjectNode();
				body.put("allThreadsContinued", true);
				respond(requestSequence, command, body);
				publish("{CONTROL} continue");
				event("continued", MAPPER.createObjectNode());
				advance();
				stopped(breakpoints.isEmpty() ? "step" : "breakpoint");
			}
			case "next", "stepIn", "stepOut" -> {
				respond(requestSequence, command, null);
				publish("{CONTROL} " + command);
				advance();
				stopped("step");
			}
			case "pause" -> {
				respond(requestSequence, command, null);
				publish("{CONTROL} pause");
				stopped("pause");
			}
			case "threads" -> {
				ObjectNode thread = MAPPER.createObjectNode();
				thread.put("id", 1);
				thread.put("name", "main");
				ObjectNode body = MAPPER.createObjectNode();
				ArrayNode threads = MAPPER.createArrayNode();
				threads.add(thread);
				body.set("threads", threads);
				respond(requestSequence, command, body);
			}
			case "stackTrace" -> {
				ObjectNode frame = MAPPER.createObjectNode();
				frame.put("id", 1);
				frame.put("name", "main");
				frame.put("line", currentLine);
				frame.put("column", 1);
				ObjectNode body = MAPPER.createObjectNode();
				ArrayNode frames = MAPPER.createArrayNode();
				frames.add(frame);
				body.set("stackFrames", frames);
				body.put("totalFrames", 1);
				respond(requestSequence, command, body);
			}
			case "disconnect" -> {
				respond(requestSequence, command, null);
				terminated();
				output.flush();
				System.exit(0);
			}
			default -> respond(requestSequence, command, null);
		}
	}

	private void advance() {
		currentLine++;
	}

	private String joinedBreakpoints() {
		StringBuilder text = new StringBuilder();
		for (Integer line : breakpoints) {
			if (text.length() > 0) {
				text.append(',');
			}
			text.append(line);
		}
		return text.toString();
	}

	private void requestRunInTerminal() throws IOException {
		String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe"
				: "java";
		String javaExecutable = Path.of(System.getProperty("java.home"), "bin", executable).toString();
		ObjectNode arguments = MAPPER.createObjectNode();
		arguments.put("kind", "integrated");
		arguments.put("title", "Echo stdin");
		ArrayNode args = arguments.putArray("args");
		args.add(javaExecutable);
		args.add("-cp");
		args.add(echoClasspath);
		args.add(EchoStdin.class.getName());

		ObjectNode request = MAPPER.createObjectNode();
		request.put("seq", ++sequence);
		request.put("type", "request");
		request.put("command", "runInTerminal");
		request.set("arguments", arguments);
		send(request);
	}

	private void stopped(String reason) throws IOException {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("reason", reason);
		body.put("threadId", 1);
		body.put("allThreadsStopped", true);
		ObjectNode event = MAPPER.createObjectNode();
		event.put("type", "event");
		event.put("event", "stopped");
		event.set("body", body);
		send(event);
	}

	private void terminated() throws IOException {
		if (terminatedSent) {
			return;
		}
		terminatedSent = true;
		event("terminated", MAPPER.createObjectNode());
	}

	private void publish(String text) throws IOException {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("category", "stdout");
		body.put("output", text + "\n");
		event("output", body);
	}

	private void event(String name, ObjectNode body) throws IOException {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("seq", ++sequence);
		node.put("type", "event");
		node.put("event", name);
		if (body != null && !body.isEmpty()) {
			node.set("body", body);
		}
		send(node);
	}

	private void respond(int requestSequence, String command, ObjectNode body) throws IOException {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("seq", ++sequence);
		node.put("type", "response");
		node.put("request_seq", requestSequence);
		node.put("success", true);
		node.put("command", command);
		if (body != null) {
			node.set("body", body);
		}
		send(node);
	}

	private void respondError(int requestSequence, String command, String message) throws IOException {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("seq", ++sequence);
		node.put("type", "response");
		node.put("request_seq", requestSequence);
		node.put("success", false);
		node.put("command", command);
		node.put("message", message);
		ObjectNode error = MAPPER.createObjectNode();
		error.put("id", 3000);
		error.put("format", message);
		error.put("showUser", true);
		node.set("body", MAPPER.createObjectNode().set("error", error));
		send(node);
	}

	private void send(JsonNode node) throws IOException {
		byte[] body = MAPPER.writeValueAsString(node).getBytes(StandardCharsets.UTF_8);
		output.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		output.write(body);
		output.flush();
	}

	private JsonNode readMessage() throws IOException {
		StringBuilder header = new StringBuilder();
		int read;
		while ((read = input.read()) != -1) {
			header.append((char) read);
			if (header.length() >= 4 && header.charAt(header.length() - 4) == '\r'
					&& header.charAt(header.length() - 3) == '\n' && header.charAt(header.length() - 2) == '\r'
					&& header.charAt(header.length() - 1) == '\n') {
				break;
			}
		}
		if (read == -1) {
			return null;
		}
		int length = contentLength(header.toString());
		if (length < 0) {
			return null;
		}
		byte[] body = input.readNBytes(length);
		if (body.length < length) {
			return null;
		}
		return MAPPER.readTree(body);
	}

	private static int contentLength(String header) {
		for (String line : header.split("\r\n")) {
			int colon = line.indexOf(':');
			if (colon > 0 && "content-length".equalsIgnoreCase(line.substring(0, colon).trim())) {
				try {
					return Integer.parseInt(line.substring(colon + 1).trim());
				}
				catch (NumberFormatException error) {
					return -1;
				}
			}
		}
		return -1;
	}

}
