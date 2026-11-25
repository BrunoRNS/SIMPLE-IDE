package io.simpleide.dap;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;

/**
 * Downloads the {@code vscode-java-debug} extension and exposes its JDT LS bundle.
 *
 * <p>
 * Java debugging is hosted by the Eclipse JDT language server and needs the
 * {@code com.microsoft.java.debug.plugin} bundle, which is shipped inside the
 * {@code vscode-java-debug} extension. The extension is downloaded from Open VSX at
 * runtime, like the language server itself, instead of being bundled with the IDE. The
 * newest extension released on Open VSX is used when its plugin runs on the JDK in use;
 * otherwise the newest release known to work on Java 11+ is selected. The bundle version
 * is verified from the jar manifest, so an incompatible release is never loaded.
 *
 * <p>
 * Installations are cached under {@code ~/.simpleide/dap/java-debug-<extension version>},
 * and a matching {@code .installed} marker avoids network access on later starts.
 *
 * <p>
 * Covered by {@code JavaDebugInstallerTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class JavaDebugInstaller {

	private static final Logger LOGGER = LoggerFactory.getLogger(JavaDebugInstaller.class);

	/**
	 * Open VSX metadata endpoint that reports the newest extension release.
	 */
	public static final String LATEST_VERSION_URL = "https://open-vsx.org/api/vscjava/vscode-java-debug/latest";

	/**
	 * Download URL template for an extension release.
	 */
	public static final String DOWNLOAD_URL_TEMPLATE = "https://open-vsx.org/api/vscjava/vscode-java-debug/%s/file/"
			+ "vscjava.vscode-java-debug-%s.vsix";

	/**
	 * Newest release that still ships a plugin compatible with Java 11 and 17.
	 */
	public static final String JAVA_11_COMPATIBLE_VERSION = "0.57.0";

	private static final Pattern EXECUTION_ENVIRONMENT = Pattern.compile("JavaSE-(\\d+)");

	private static final Pattern OSGI_EXECUTION_ENVIRONMENT = Pattern.compile("osgi\\.ee=JavaSE\\)\\(version=(\\d+)");

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private static final ConcurrentMap<Path, Object> INSTALL_LOCKS = new ConcurrentHashMap<>();

	private JavaDebugInstaller() {
	}

	/**
	 * Installs the newest extension compatible with the running JDK.
	 * @return the path of the bundled {@code com.microsoft.java.debug.plugin} jar
	 * @throws IOException when no compatible release is found or the download fails
	 * @throws InterruptedException when the calling thread is interrupted
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static Path install() throws IOException, InterruptedException {
		return install(Runtime.version().feature());
	}

	/**
	 * Installs the newest extension compatible with a JDK feature release.
	 * @param javaFeature JDK feature release, for example {@code 21}
	 * @return the path of the bundled {@code com.microsoft.java.debug.plugin} jar
	 * @throws IOException when no compatible release is found or the download fails
	 * @throws InterruptedException when the calling thread is interrupted
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static Path install(int javaFeature) throws IOException, InterruptedException {
		if (javaFeature <= 0) {
			throw new IOException("A valid Java feature release is required to install the Java debugger.");
		}
		Path cached = findCompatibleInstallation(javaFeature);
		if (cached != null) {
			return cached;
		}
		String latest = latestVersion();
		Path installed = installVersion(latest, javaFeature);
		if (installed != null) {
			return installed;
		}
		if (!JAVA_11_COMPATIBLE_VERSION.equals(latest)) {
			LOGGER.info("vscode-java-debug {} requires a newer JDK; falling back to {}.", latest,
					JAVA_11_COMPATIBLE_VERSION);
			installed = installVersion(JAVA_11_COMPATIBLE_VERSION, javaFeature);
			if (installed != null) {
				return installed;
			}
		}
		throw new IOException("No vscode-java-debug release compatible with Java " + javaFeature + " was found.");
	}

	/**
	 * Returns the installation directory of an extension version.
	 * @param version extension version
	 * @return the installation directory
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static Path getInstallDirectory(String version) {
		return Paths.get(System.getProperty("user.home"), ".simpleide", "dap", "java-debug-" + version);
	}

	/**
	 * Returns the Java feature release required by a plugin jar.
	 * @param jar plugin jar
	 * @return the required feature release, or {@code 0} when the manifest does not state
	 * one
	 * @throws IOException when the jar manifest cannot be read
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static int requiredJavaFeature(Path jar) throws IOException {
		try (JarFile archive = new JarFile(jar.toFile())) {
			var manifest = archive.getManifest();
			if (manifest == null) {
				return 0;
			}
			String value = manifest.getMainAttributes().getValue("Require-Capability");
			if (value != null) {
				Matcher matcher = OSGI_EXECUTION_ENVIRONMENT.matcher(value);
				if (matcher.find()) {
					return Integer.parseInt(matcher.group(1));
				}
			}
			value = manifest.getMainAttributes().getValue("Bundle-RequiredExecutionEnvironment");
			if (value != null) {
				Matcher matcher = EXECUTION_ENVIRONMENT.matcher(value);
				if (matcher.find()) {
					return Integer.parseInt(matcher.group(1));
				}
			}
			return 0;
		}
	}

	private static Path findCompatibleInstallation(int javaFeature) throws IOException {
		Path root = Paths.get(System.getProperty("user.home"), ".simpleide", "dap");
		if (!Files.isDirectory(root)) {
			return null;
		}
		try (Stream<Path> directories = Files.list(root)) {
			List<Path> candidates = directories.filter(path -> path.getFileName().toString().startsWith("java-debug-"))
				.sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
				.toList();
			for (Path candidate : candidates) {
				Path jar = findPluginJar(candidate);
				if (jar == null || !Files.isRegularFile(candidate.resolve(".installed"))) {
					continue;
				}
				int required = requiredJavaFeature(jar);
				if (required <= javaFeature) {
					return jar;
				}
			}
		}
		return null;
	}

	private static Path installVersion(String version, int javaFeature) throws IOException, InterruptedException {
		Path directory = getInstallDirectory(version);
		Object lock = INSTALL_LOCKS.computeIfAbsent(directory, ignored -> new Object());
		synchronized (lock) {
			Path existing = findPluginJar(directory);
			if (existing != null && Files.isRegularFile(directory.resolve(".installed"))) {
				int required = requiredJavaFeature(existing);
				return required <= javaFeature ? existing : null;
			}
			try {
				deleteDirectory(directory);
				Files.createDirectories(directory);
				Path archive = download(version, directory);
				try {
					ArchiverFactory.createArchiver(archive.toFile()).extract(archive.toFile(), directory.toFile());
				}
				finally {
					Files.deleteIfExists(archive);
				}
				Path jar = findPluginJar(directory);
				if (jar == null) {
					throw new IOException("The vscode-java-debug " + version + " archive contains no debug plugin.");
				}
				int required = requiredJavaFeature(jar);
				if (required > javaFeature) {
					LOGGER.info("vscode-java-debug {} requires Java {} but the running JDK is Java {}.", version,
							required, javaFeature);
					deleteDirectory(directory);
					return null;
				}
				Files.writeString(directory.resolve(".installed"), version, StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
				LOGGER.info("Java debug adapter vscode-java-debug {} installed successfully.", version);
				return jar;
			}
			catch (IOException | InterruptedException | RuntimeException error) {
				try {
					deleteDirectory(directory);
				}
				catch (IOException cleanupError) {
					error.addSuppressed(cleanupError);
				}
				if (error instanceof InterruptedException interruptedError) {
					Thread.currentThread().interrupt();
					throw interruptedError;
				}
				if (error instanceof IOException ioError) {
					throw ioError;
				}
				throw new IOException("Unable to install the Java debug adapter " + version, error);
			}
		}
	}

	private static String latestVersion() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_VERSION_URL))
			.timeout(Duration.ofMinutes(2))
			.header("User-Agent", "SimpleIDE")
			.GET()
			.build();
		HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException(
					"Unable to resolve the newest vscode-java-debug release: HTTP " + response.statusCode());
		}
		try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
			JsonObject json = reader.readObject();
			String version = json.getString("version", "");
			if (version.isBlank()) {
				throw new IOException("The vscode-java-debug release metadata contains no version.");
			}
			return version;
		}
		catch (RuntimeException error) {
			throw new IOException("Unable to read the vscode-java-debug release metadata.", error);
		}
	}

	private static Path download(String version, Path directory) throws IOException, InterruptedException {
		URI uri = URI.create(String.format(DOWNLOAD_URL_TEMPLATE, version, version));
		Output.getInstance().publish(OutputType.DEBUG, "Downloading vscode-java-debug " + version + " from " + uri);
		Path destination = Files.createTempFile(directory, "java-debug.", ".zip");
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofMinutes(5))
			.header("User-Agent", "SimpleIDE")
			.GET()
			.build();
		HttpResponse<Path> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(destination,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			Files.deleteIfExists(destination);
			throw new IOException("HTTP " + response.statusCode() + " while downloading vscode-java-debug " + version);
		}
		return destination;
	}

	private static Path findPluginJar(Path directory) {
		if (!Files.isDirectory(directory)) {
			return null;
		}
		try (Stream<Path> files = Files.walk(directory)) {
			return files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().startsWith("com.microsoft.java.debug.plugin-"))
				.filter(path -> path.getFileName().toString().endsWith(".jar"))
				.findFirst()
				.orElse(null);
		}
		catch (IOException error) {
			LOGGER.debug("Unable to inspect the Java debug adapter installation at {}.", directory, error);
			return null;
		}
	}

	private static void deleteDirectory(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

}
