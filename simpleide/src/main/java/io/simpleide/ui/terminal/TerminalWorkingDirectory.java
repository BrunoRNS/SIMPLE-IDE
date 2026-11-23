package io.simpleide.ui.terminal;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the initial directory for a terminal session.
 * 
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class TerminalWorkingDirectory {

	private static final Logger LOGGER = LoggerFactory.getLogger(TerminalWorkingDirectory.class);

	private TerminalWorkingDirectory() {
	}

	/**
	 * Selects the open workspace, the accessible home directory, or the application
	 * startup directory, in that order.
	 * @param workspace open Explorer root, or {@code null} if no folder is open
	 * @param homeDirectory configured user home path
	 * @param startupDirectory application startup directory
	 * @return the directory to use for a new terminal session
	 */
	public static Path resolve(Path workspace, String homeDirectory, String startupDirectory) {
		if (workspace != null) {
			return workspace.toAbsolutePath().normalize();
		}

		Path home = accessibleDirectory(homeDirectory);
		if (home != null) {
			return home;
		}

		Path startup = accessibleDirectory(startupDirectory);
		if (startup != null) {
			LOGGER.warn("User home directory '{}' is inaccessible; starting terminal in '{}'.", homeDirectory, startup);
			return startup;
		}

		throw new DirectoryUnavailableException("Neither the user home directory nor the application startup directory "
				+ "is accessible. user.home='" + homeDirectory + "', user.dir='" + startupDirectory + "'.");
	}

	static Path resolveDefault() {
		return resolve(null, System.getProperty("user.home"), System.getProperty("user.dir"));
	}

	private static Path accessibleDirectory(String directory) {
		if (directory == null || directory.isBlank()) {
			return null;
		}

		try {
			Path path = Path.of(directory).toAbsolutePath().normalize();
			return Files.isDirectory(path) && Files.isReadable(path) ? path : null;
		}
		catch (InvalidPathException | SecurityException exception) {
			LOGGER.warn("Unable to access terminal working directory '{}'.", directory, exception);
			return null;
		}
	}

	static final class DirectoryUnavailableException extends IllegalStateException {

		private DirectoryUnavailableException(String message) {
			super(message);
		}

	}

}
