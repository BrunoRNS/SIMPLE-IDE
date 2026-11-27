package io.simpleide.dap;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.io.Output;
import io.simpleide.language.io.OutputType;
import io.simpleide.utils.ArchiveUtil;
import io.simpleide.utils.DownloadUtil;
import io.simpleide.utils.FileUtil;

/**
 * Downloads and extracts debug adapters into the user directory.
 *
 * <p>
 * Adapters are installed under {@code ~/.simpleide/dap/<id>-<version>}. A
 * {@code .installed} marker stores the version; a matching marker reuses the existing
 * installation without network access. Any failure removes the partial installation and
 * is reported to the caller, which disables the Debug action until the next successful
 * attempt.
 *
 * <p>
 * Covered by {@code DapInstallerTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class DapInstaller {

	private static final Logger LOGGER = LoggerFactory.getLogger(DapInstaller.class);

	private static final ConcurrentMap<Path, Object> INSTALL_LOCKS = new ConcurrentHashMap<>();

	private DapInstaller() {
	}

	/**
	 * Installs an adapter unless its installed version already matches.
	 * @param adapter adapter to install
	 * @param download platform download entry
	 * @return the installation directory
	 * @throws IOException when the download or extraction fails
	 * @throws InterruptedException when the calling thread is interrupted
	 */
	public static Path install(DapAdapter adapter, DapDownload download) throws IOException, InterruptedException {
		Path directory = getInstallDirectory(adapter);
		Object lock = INSTALL_LOCKS.computeIfAbsent(directory, ignored -> new Object());
		synchronized (lock) {
			if (isInstalled(adapter)) {
				return directory;
			}
			try {
				FileUtil.deleteRecursively(directory);
				Files.createDirectories(directory);
				Path archive = download(adapter, download, directory);
				try {
					if (download.archiveType() == null || download.archiveType().isBlank()) {
						Files.move(archive, directory.resolve("adapter"));
					}
					else {
						ArchiveUtil.extract(archive, directory);
					}
				}
				finally {
					Files.deleteIfExists(archive);
				}
				makeCommandExecutable(adapter, directory);
				Files.writeString(directory.resolve(".installed"), adapter.getVersion(), StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
				LOGGER.info("DAP adapter {} {} installed successfully.", adapter.getName(), adapter.getVersion());
				return directory;
			}
			catch (IOException | InterruptedException | RuntimeException error) {
				try {
					FileUtil.deleteRecursively(directory);
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
				throw new IOException("Unable to install debug adapter " + adapter.getName(), error);
			}
		}
	}

	/**
	 * Checks whether the adapter version is recorded as installed.
	 * @param adapter adapter to check
	 * @return {@code true} when the installation marker matches the adapter version
	 */
	public static boolean isInstalled(DapAdapter adapter) {
		Path marker = getInstallDirectory(adapter).resolve(".installed");
		if (!Files.isRegularFile(marker)) {
			return false;
		}
		try {
			return Files.readString(marker).trim().equals(adapter.getVersion());
		}
		catch (IOException error) {
			LOGGER.debug("Unable to read the installed-version marker for {}.", adapter.getName(), error);
			return false;
		}
	}

	/**
	 * Returns the directory where an adapter is installed.
	 * @param adapter adapter to resolve
	 * @return the installation directory
	 */
	public static Path getInstallDirectory(DapAdapter adapter) {
		return Paths.get(System.getProperty("user.home"), ".simpleide", "dap",
				adapter.getId() + "-" + adapter.getVersion());
	}

	private static Path download(DapAdapter adapter, DapDownload download, Path directory)
			throws IOException, InterruptedException {
		URI uri = URI.create(download.url());
		DownloadUtil.requireHttps(uri);
		Output.getInstance().publish(OutputType.DEBUG, "Downloading " + adapter.getName() + " from " + uri);
		String suffix = download.archiveType() == null || download.archiveType().isBlank() ? ".bin"
				: "." + download.archiveType();
		return DownloadUtil.download(uri, directory, "dap-download.", suffix);
	}

	private static void makeCommandExecutable(DapAdapter adapter, Path directory) throws IOException {
		String command = adapter.commandLine(directory.toString(), "").get(0);
		Path executable = directory.resolve(command);
		if (!executable.normalize().startsWith(directory.normalize()) || !Files.isRegularFile(executable)) {
			return;
		}
		if (!executable.toFile().setExecutable(true, true)) {
			LOGGER.debug("Unable to mark the debug adapter command as executable: {}", executable);
		}
	}

}
