package io.simpleide.dap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import io.simpleide.language.config.LangPlatform;

/**
 * Coordinates debug adapter discovery, installation and session startup.
 *
 * <p>
 * The manager resolves the adapter registered for the current operating system and
 * architecture, installs it on first use and then creates a {@link DapSession}. When the
 * platform has no adapter, or when the download fails, {@link #prepare()} throws
 * {@link DapUnavailableException} with a message suitable for the user; callers disable
 * the Debug action until the next attempt.
 *
 * <p>
 * Covered by {@code DapRegistryTest} and {@code DapManagerTest}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class DapManager {

	private final DapRegistry registry;

	private final LangPlatform platform;

	/**
	 * Creates a manager for a registry and platform.
	 * @param registry adapter registry
	 * @param platform platform used to select downloads
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapManager(DapRegistry registry, LangPlatform platform) {
		this.registry = registry;
		this.platform = platform;
	}

	/**
	 * Creates a manager backed by the bundled registry and the detected platform.
	 * @return the default manager
	 * @throws DapUnavailableException when the bundled registry cannot be read
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static DapManager createDefault() throws DapUnavailableException {
		try {
			return new DapManager(DapRegistry.loadDefault(), LangPlatform.getCurrentPlatform());
		}
		catch (RuntimeException error) {
			throw new DapUnavailableException("Unable to read the debug adapter registry.", error);
		}
	}

	/**
	 * Returns whether an adapter is registered for this platform.
	 * @return {@code true} when debugging can be offered
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean isSupported() {
		return registry.supports(platform);
	}

	/**
	 * Resolves and installs the adapter for this platform.
	 * @return the installed adapter and its download entry
	 * @throws DapUnavailableException when the platform is unsupported or installation
	 * fails
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public PreparedAdapter prepare() throws DapUnavailableException {
		DapAdapter adapter = registry.findForPlatform(platform);
		if (adapter == null) {
			throw new DapUnavailableException("Debugging is not supported on " + platform.getDisplayName() + ".");
		}
		DapDownload download = adapter.downloadFor(platform);
		if (download == null) {
			throw new DapUnavailableException(
					"No " + adapter.getName() + " download is registered for " + platform.getDisplayName() + ".");
		}
		try {
			Path directory = DapInstaller.install(adapter, download);
			return new PreparedAdapter(adapter, download, directory);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new DapUnavailableException("Debug adapter installation was interrupted.", error);
		}
		catch (IOException | RuntimeException error) {
			throw new DapUnavailableException(
					"Unable to download and install " + adapter.getName() + ": " + error.getMessage(), error);
		}
	}

	/**
	 * Starts a debug session for a program.
	 * @param program program to debug
	 * @param breakpoints initial line breakpoints
	 * @param listener listener for execution state changes
	 * @return the started session
	 * @throws DapUnavailableException when the adapter is unavailable or the session
	 * cannot start
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapSession start(Path program, List<Integer> breakpoints, DapSession.Listener listener)
			throws DapUnavailableException {
		PreparedAdapter prepared = prepare();
		DapSession session = new DapSession(prepared.adapter(), prepared.installDirectory(), program, listener);
		try {
			session.start(breakpoints);
			return session;
		}
		catch (IOException error) {
			session.close();
			throw new DapUnavailableException(
					"Unable to start " + prepared.adapter().getName() + ": " + error.getMessage(), error);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			session.close();
			throw new DapUnavailableException("Debug adapter startup was interrupted.", error);
		}
	}

	/**
	 * Installed adapter ready to launch a session.
	 *
	 * @param adapter adapter definition
	 * @param download platform download entry
	 * @param installDirectory installation directory
	 * @author BrunoRNS
	 * @since v1.1.0
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public record PreparedAdapter(DapAdapter adapter, DapDownload download, Path installDirectory) {
	}

	/**
	 * Signals that debugging cannot start on this machine.
	 *
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static final class DapUnavailableException extends Exception {

		/**
		 * Creates an unavailable exception with a user-facing message.
		 * @param message explanation shown to the user
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		public DapUnavailableException(String message) {
			super(message);
		}

		/**
		 * Creates an unavailable exception with a user-facing message and cause.
		 * @param message explanation shown to the user
		 * @param cause underlying failure
		 * @author BrunoRNS
		 * @since v1.1.0
		 */
		public DapUnavailableException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
