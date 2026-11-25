package io.simpleide.dap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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

	/**
	 * Transport used by adapters hosted by a language server instead of a downloaded
	 * process, such as the Java debug adapter inside the JDT language server.
	 */
	public static final String JDTLS_TRANSPORT = "jdtls";

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
	 * Returns whether a program can be debugged on this platform.
	 * @param program source file
	 * @return {@code true} when an adapter is registered for the file language
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean supports(Path program) {
		String language = languageOf(program);
		return !language.isEmpty() && registry.supports(platform, language);
	}

	/**
	 * Resolves the language identifier used to select a debug adapter.
	 * @param program source file
	 * @return the language identifier, or an empty string when the language is not
	 * debuggable
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public static String languageOf(Path program) {
		if (program == null || program.getFileName() == null) {
			return "";
		}
		String name = program.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		String extension = dot < 0 ? "" : name.substring(dot + 1);
		return switch (extension) {
			case "js", "jsx", "mjs", "cjs", "jscript" -> "javascript";
			case "py", "pyi", "pyw", "py3" -> "python";
			case "go" -> "go";
			case "java", "jav" -> "java";
			default -> "";
		};
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
		return prepare(adapter);
	}

	/**
	 * Resolves and installs the adapter for a program language.
	 * @param program source file
	 * @return the installed adapter and its download entry
	 * @throws DapUnavailableException when the language is unsupported or installation
	 * fails
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public PreparedAdapter prepare(Path program) throws DapUnavailableException {
		String language = languageOf(program);
		if (language.isEmpty()) {
			throw new DapUnavailableException("Debugging is not supported for " + program.getFileName() + ".");
		}
		DapAdapter adapter = registry.findForPlatform(platform, language);
		if (adapter == null) {
			throw new DapUnavailableException(
					"Debugging " + language + " is not supported on " + platform.getDisplayName() + ".");
		}
		if (JDTLS_TRANSPORT.equals(adapter.getTransport())) {
			throw new DapUnavailableException(
					"Debugging " + language + " runs inside the language server, which has no download to install.");
		}
		return prepare(adapter);
	}

	private PreparedAdapter prepare(DapAdapter adapter) throws DapUnavailableException {
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
		if (isLanguageServerDebug(program)) {
			throw new DapUnavailableException(
					"Debugging " + languageOf(program) + " runs through the language server; use startJava.");
		}
		PreparedAdapter prepared = prepare(program);
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
		catch (RuntimeException error) {
			session.close();
			throw new DapUnavailableException(
					"Unable to start " + prepared.adapter().getName() + ": " + error.getMessage(), error);
		}
	}

	/**
	 * Starts a Java debug session through the JDT language server.
	 *
	 * <p>
	 * The Java debug adapter is hosted by the language server, so no standalone adapter
	 * is downloaded. The language server must already be initialized and the
	 * {@code vscode.java.startDebugSession} command must be available.
	 * @param program Java source file to debug
	 * @param client language-server command client
	 * @param breakpoints initial line breakpoints
	 * @param listener listener for execution state changes
	 * @return the started session
	 * @throws DapUnavailableException when the language server cannot start a session
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public DapSession startJava(Path program, JavaDebugBridge.JavaDebugClient client, List<Integer> breakpoints,
			DapSession.Listener listener) throws DapUnavailableException {
		if (client == null) {
			throw new DapUnavailableException(
					"Java debugging requires the Java language server. Open the file and wait for it to connect.");
		}
		try {
			return JavaDebugBridge.start(client, program, breakpoints, listener);
		}
		catch (IOException error) {
			throw new DapUnavailableException("Unable to start Java debugging: " + error.getMessage(), error);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new DapUnavailableException("Java debug startup was interrupted.", error);
		}
		catch (RuntimeException error) {
			throw new DapUnavailableException("Unable to start Java debugging: " + error.getMessage(), error);
		}
	}

	/**
	 * Returns whether a program is debugged through a language server rather than a
	 * standalone adapter.
	 * @param program source file
	 * @return {@code true} when the resolved adapter uses the {@code jdtls} transport
	 * @author BrunoRNS
	 * @since v1.1.0
	 */
	public boolean isLanguageServerDebug(Path program) {
		String language = languageOf(program);
		if (language.isEmpty()) {
			return false;
		}
		DapAdapter adapter = registry.findForPlatform(platform, language);
		return adapter != null && JDTLS_TRANSPORT.equals(adapter.getTransport());
	}

	/**
	 * Installed adapter ready to launch a session.
	 *
	 * @param adapter adapter definition
	 * @param download platform download entry
	 * @param installDirectory installation directory
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
