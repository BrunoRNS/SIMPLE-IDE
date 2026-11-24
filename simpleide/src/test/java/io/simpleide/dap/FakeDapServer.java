package io.simpleide.dap;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Debug Adapter Protocol server used by the DAP session tests.
 *
 * <p>
 * The server speaks the DAP framing over standard input/output or TCP and answers the
 * requests a client sends during startup: {@code initialize}, {@code launch},
 * {@code setBreakpoints}, {@code configurationDone} and {@code threads}. After
 * configuration is done it emits a {@code stopped} event and then a {@code terminated}
 * event, so tests can assert that {@link DapSession} completes the handshake and reports
 * execution state changes. It is launched as a child process, which keeps the tests
 * independent of any external debug adapter.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class FakeDapServer {

	private static int sequence;

	private static String lastCommand = "";

	private FakeDapServer() {
	}

	/**
	 * Runs the fake server.
	 * @param args {@code tcp <port>} for a TCP server, or no arguments for stdio
	 * @throws IOException when the transport cannot be opened
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static void main(String[] args) throws IOException {
		if (args.length >= 2 && "tcp".equals(args[0])) {
			int port = Integer.parseInt(args[1]);
			try (ServerSocket serverSocket = new ServerSocket(port)) {
				try (Socket socket = serverSocket.accept()) {
					serve(socket.getInputStream(), socket.getOutputStream());
				}
			}
		}
		else {
			serve(System.in, System.out);
		}
	}

	private static void serve(InputStream input, OutputStream output) throws IOException {
		BufferedOutputStream writer = new BufferedOutputStream(output);
		byte[] buffer = new byte[8192];
		int pending = 0;
		while (true) {
			int read = input.read(buffer);
			if (read < 0) {
				return;
			}
			pending = process(writer, buffer, read, pending);
		}
	}

	private static int process(BufferedOutputStream writer, byte[] buffer, int read, int pending) throws IOException {
		byte[] data = new byte[pending + read];
		System.arraycopy(buffer, 0, data, 0, pending);
		System.arraycopy(buffer, pending, data, pending, read);
		int offset = 0;
		while (true) {
			int headerEnd = indexOf(data, offset, "\r\n\r\n");
			if (headerEnd < 0) {
				break;
			}
			String header = new String(data, offset, headerEnd - offset, StandardCharsets.UTF_8);
			int length = contentLength(header);
			int bodyStart = headerEnd + 4;
			if (length < 0 || data.length < bodyStart + length) {
				break;
			}
			String body = new String(data, bodyStart, length, StandardCharsets.UTF_8);
			handle(writer, body);
			offset = bodyStart + length;
		}
		int remaining = data.length - offset;
		byte[] leftover = new byte[remaining];
		System.arraycopy(data, offset, leftover, 0, remaining);
		System.arraycopy(leftover, 0, buffer, 0, remaining);
		return remaining;
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

	private static int indexOf(byte[] data, int from, String needle) {
		byte[] target = needle.getBytes(StandardCharsets.UTF_8);
		for (int index = from; index <= data.length - target.length; index++) {
			boolean matches = true;
			for (int offset = 0; offset < target.length; offset++) {
				if (data[index + offset] != target[offset]) {
					matches = false;
					break;
				}
			}
			if (matches) {
				return index;
			}
		}
		return -1;
	}

	private static void handle(BufferedOutputStream writer, String body) throws IOException {
		String command = extract(body, "\"command\"");
		if (command == null) {
			return;
		}
		lastCommand = command;
		int requestSeq = extractInt(body, "\"seq\"");
		switch (command) {
			case "initialize" -> {
				respond(writer, requestSeq, "{\"supportsConfigurationDoneRequest\":true}");
				send(writer, "{\"type\":\"event\",\"event\":\"initialized\",\"body\":{}}");
			}
			case "setBreakpoints" -> respond(writer, requestSeq,
					"{\"breakpoints\":[{\"verified\":true,\"line\":1},{\"verified\":true,\"line\":2}]}");
			case "configurationDone" -> {
				respond(writer, requestSeq, "{}");
				send(writer,
						"{\"type\":\"event\",\"event\":\"stopped\",\"body\":{\"reason\":\"breakpoint\",\"threadId\":1}}");
				send(writer, "{\"type\":\"event\",\"event\":\"terminated\",\"body\":{}}");
			}
			case "threads" -> respond(writer, requestSeq, "{\"threads\":[{\"id\":1,\"name\":\"main\"}]}");
			case "disconnect" -> {
				respond(writer, requestSeq, "{}");
				writer.flush();
				System.exit(0);
			}
			default -> respond(writer, requestSeq, "{}");
		}
	}

	private static void respond(BufferedOutputStream writer, int requestSeq, String result) throws IOException {
		send(writer, "{\"type\":\"response\",\"seq\":" + (++sequence) + ",\"request_seq\":" + requestSeq
				+ ",\"success\":true,\"command\":\"" + lastCommand + "\",\"body\":" + result + "}");
	}

	private static void send(BufferedOutputStream writer, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		String header = "Content-Length: " + body.length + "\r\n\r\n";
		writer.write(header.getBytes(StandardCharsets.UTF_8));
		writer.write(body);
		writer.flush();
	}

	private static String extract(String body, String key) {
		int index = body.indexOf(key);
		if (index < 0) {
			return null;
		}
		int start = body.indexOf('"', index + key.length());
		if (start < 0) {
			return null;
		}
		int end = body.indexOf('"', start + 1);
		return end < 0 ? null : body.substring(start + 1, end);
	}

	private static int extractInt(String body, String key) {
		int index = body.indexOf(key);
		if (index < 0) {
			return 0;
		}
		int start = index + key.length();
		while (start < body.length() && !Character.isDigit(body.charAt(start))) {
			start++;
		}
		int end = start;
		while (end < body.length() && Character.isDigit(body.charAt(end))) {
			end++;
		}
		try {
			return Integer.parseInt(body.substring(start, end));
		}
		catch (NumberFormatException error) {
			return 0;
		}
	}

}