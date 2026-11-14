package io.simpleide.ui.terminal.core.busybox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Temporary copy of the embedded BusyBox binary.
 *
 * <p>
 * Extraction creates a private temporary directory, copies the platform resource into it
 * and, on Unix-like systems, marks it executable. Closing the instance deletes both the
 * binary and the directory.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class BusyBoxExecutable implements AutoCloseable {

	private final Path directory;

	private final Path path;

	private BusyBoxExecutable(Path directory, Path path) {
		this.directory = directory;
		this.path = path;
	}

	static BusyBoxExecutable create(String fileName) throws IOException {
		Path directory = Files.createTempDirectory("simpleide-busybox-");
		return new BusyBoxExecutable(directory, directory.resolve(fileName));
	}

	void copyFrom(Class<?> resourceOwner, String resourcePath) throws IOException {
		try (InputStream input = resourceOwner
			.getResourceAsStream("/io/simpleide/ui/terminal/core/busybox/" + resourcePath)) {
			if (input == null) {
				throw new IOException("Embedded BusyBox resource not found: " + resourcePath);
			}
			Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	void makeExecutable() throws IOException {
		if (!path.toFile().setExecutable(true, true) && !Files.isExecutable(path)) {
			throw new IOException("Unable to make embedded BusyBox executable: " + path);
		}
	}

	/**
	 * Returns the path of the extracted executable.
	 * @return the temporary executable path
	 */
	public Path getPath() {
		return path;
	}

	/**
	 * Deletes the extracted executable and its temporary directory.
	 * @throws IOException when a file or the directory cannot be deleted
	 */
	@Override
	public void close() throws IOException {
		IOException failure = null;
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException exception) {
			failure = exception;
		}
		try {
			Files.deleteIfExists(directory);
		}
		catch (IOException exception) {
			if (failure == null) {
				failure = exception;
			}
			else {
				failure.addSuppressed(exception);
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

}
