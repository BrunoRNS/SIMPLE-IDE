package io.simpleide.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link DownloadUtil} against a local HTTP server.
 */
class DownloadUtilTest {

	@TempDir
	Path directory;

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void rejectsPlainHttpUrls() {
		assertThatThrownBy(() -> DownloadUtil.requireHttps(URI.create("http://example.com/file.zip")))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("HTTPS");
	}

	@Test
	void acceptsHttpsUrls() {
		assertThatCode(() -> DownloadUtil.requireHttps(URI.create("https://example.com/file.zip")))
			.doesNotThrowAnyException();
	}

	@Test
	void downloadsToATemporaryFile() throws Exception {
		URI uri = startServer("/file.zip", 200, "archive");

		Path file = DownloadUtil.download(uri, directory, "download.", ".zip");

		assertThat(file).hasParent(directory).hasContent("archive");
		assertThat(file.getFileName().toString()).startsWith("download.").endsWith(".zip");
	}

	@Test
	void removesThePartialFileAfterAnHttpError() throws Exception {
		URI uri = startServer("/missing.zip", 404, "");

		assertThatThrownBy(() -> DownloadUtil.download(uri, directory, "download.", ".zip"))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("404");
		try (var files = Files.list(directory)) {
			assertThat(files.toList()).isEmpty();
		}
	}

	@Test
	void downloadsTextBodies() throws Exception {
		URI uri = startServer("/metadata.json", 200, "{\"version\":\"1.2.3\"}");

		assertThat(DownloadUtil.downloadString(uri, Duration.ofSeconds(30))).isEqualTo("{\"version\":\"1.2.3\"}");
	}

	private URI startServer(String path, int status, String body) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext(path, exchange -> {
			byte[] payload = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, payload.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(payload);
			}
		});
		server.start();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

}
