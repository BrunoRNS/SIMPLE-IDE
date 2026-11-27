package io.simpleide.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;

/**
 * Downloads HTTP resources into files and strings.
 *
 * <p>
 * All requests share one client with redirects enabled and identify the IDE through the
 * {@code User-Agent} header. A file download is written to a new temporary file; a failed
 * request removes the partial file before the error is rethrown.
 *
 * <p>
 * Covered by {@code DownloadUtilTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 * @author LuisOtavio13
 * @since v1.1.0
 */
public final class DownloadUtil {

	private static final String USER_AGENT = "SimpleIDE";

	private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private DownloadUtil() {
	}

	/**
	 * Downloads a URL into a temporary file.
	 * @param uri URL to download
	 * @param directory directory that receives the temporary file
	 * @param prefix temporary file name prefix
	 * @param suffix temporary file name suffix, or {@code null} for the default suffix
	 * @return the downloaded file
	 * @throws IOException when the request fails, returns a non-2xx status or the file
	 * cannot be written
	 * @throws InterruptedException when the calling thread is interrupted
	 */
	public static Path download(URI uri, Path directory, String prefix, String suffix)
			throws IOException, InterruptedException {
		Objects.requireNonNull(uri, "uri");
		Objects.requireNonNull(directory, "directory");
		Path destination = Files.createTempFile(directory, prefix, suffix);
		try {
			HttpResponse<Path> response = send(uri, HttpResponse.BodyHandlers.ofFile(destination,
					StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), DOWNLOAD_TIMEOUT);
			if (!isSuccessful(response.statusCode())) {
				throw new IOException("HTTP " + response.statusCode() + " while downloading " + uri);
			}
			return destination;
		}
		catch (IOException | InterruptedException | RuntimeException error) {
			deleteOnFailure(destination, error);
			throw error;
		}
	}

	/**
	 * Downloads a URL and returns its response body as text.
	 * @param uri URL to download
	 * @param timeout request timeout
	 * @return the response body
	 * @throws IOException when the request fails or returns a non-2xx status
	 * @throws InterruptedException when the calling thread is interrupted
	 */
	public static String downloadString(URI uri, Duration timeout) throws IOException, InterruptedException {
		Objects.requireNonNull(uri, "uri");
		Objects.requireNonNull(timeout, "timeout");
		HttpResponse<String> response = send(uri, HttpResponse.BodyHandlers.ofString(), timeout);
		if (!isSuccessful(response.statusCode())) {
			throw new IOException("HTTP " + response.statusCode() + " while downloading " + uri);
		}
		return response.body();
	}

	/**
	 * Rejects URLs that do not use HTTPS.
	 * @param uri URL to validate
	 * @throws IOException when the URL is not HTTPS
	 */
	public static void requireHttps(URI uri) throws IOException {
		Objects.requireNonNull(uri, "uri");
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IOException("Download URL must use HTTPS: " + uri);
		}
	}

	private static <T> HttpResponse<T> send(URI uri, HttpResponse.BodyHandler<T> handler, Duration timeout)
			throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(timeout)
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();
		try {
			return HTTP_CLIENT.send(request, handler);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw error;
		}
	}

	private static boolean isSuccessful(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}

	private static void deleteOnFailure(Path destination, Exception error) {
		try {
			Files.deleteIfExists(destination);
		}
		catch (IOException cleanupError) {
			error.addSuppressed(cleanupError);
		}
	}

}
