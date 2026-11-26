package io.simpleide.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import io.simpleide.dap.DapManager;
import io.simpleide.dap.DapRegistry;
import io.simpleide.dap.DapSession;
import io.simpleide.language.config.LangPlatform;
import io.simpleide.lsp.client.LspClient;

/**
 * Facade for starting debug sessions through the Debug Adapter Protocol.
 *
 * <p>
 * Standalone adapters (JavaScript, Python and Go) are resolved from the bundled registry,
 * installed on first use and started with {@link #start}. Java is debugged through the
 * Eclipse JDT language server and the {@code vscode-java-debug} plugin, so it needs an
 * initialized {@link LspClient} and uses {@link #startJava}.
 *
 * <p>
 * The returned {@link DapSession} is owned by the caller and must be closed to terminate
 * the debugged program and the adapter. Breakpoint lines are one-based.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/dap.md">docs/api/dap.md</a>
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class Debugger {

	private Debugger() {
	}

	/**
	 * Returns the language identifier used to select a debug adapter for a file.
	 * @param file source file
	 * @return the language identifier, or an empty string when the file is not debuggable
	 */
	public static String languageOf(Path file) {
		return DapManager.languageOf(file);
	}

	/**
	 * Returns whether a file can be debugged on this platform.
	 * @param file source file
	 * @return {@code true} when a debug adapter is registered for the file language
	 */
	public static boolean isSupported(Path file) {
		if (file == null) {
			return false;
		}
		try {
			return new DapManager(DapRegistry.loadDefault(), LangPlatform.getCurrentPlatform()).supports(file);
		}
		catch (RuntimeException error) {
			return false;
		}
	}

	/**
	 * Returns whether a file is debugged through a language server instead of a
	 * downloaded adapter.
	 * @param file source file
	 * @return {@code true} when the file language uses the {@code jdtls} transport
	 */
	public static boolean isLanguageServerDebug(Path file) {
		if (file == null) {
			return false;
		}
		try {
			return new DapManager(DapRegistry.loadDefault(), LangPlatform.getCurrentPlatform())
				.isLanguageServerDebug(file);
		}
		catch (RuntimeException error) {
			return false;
		}
	}

	/**
	 * Starts a debug session for a file using a standalone adapter.
	 *
	 * <p>
	 * The adapter is downloaded and installed when it is not present yet. Java files
	 * cannot use this method because their adapter is hosted by the language server; use
	 * {@link #startJava} instead.
	 * @param file program to debug
	 * @param breakpoints initial one-based line breakpoints, may be {@code null}
	 * @param listener listener for execution state changes, may be {@code null}
	 * @return the started session
	 * @throws DapManager.DapUnavailableException when the adapter is unavailable, cannot
	 * be installed or the session fails to start
	 */
	public static DapSession start(Path file, List<Integer> breakpoints, DapSession.Listener listener)
			throws DapManager.DapUnavailableException {
		Objects.requireNonNull(file, "file");
		return DapManager.createDefault().start(file, breakpoints, listener);
	}

	/**
	 * Starts a debug session for a Java file through an initialized language server.
	 *
	 * <p>
	 * The language server must be the Eclipse JDT language server with the
	 * {@code vscode-java-debug} plugin loaded, for example the client returned by
	 * {@link LanguageServers#connect}. Without the plugin the server rejects the debug
	 * commands and this method reports the failure.
	 * @param file Java source file to debug
	 * @param languageServer initialized JDT language-server client, may be {@code null}
	 * to receive the standard "requires the Java language server" error
	 * @param breakpoints initial one-based line breakpoints, may be {@code null}
	 * @param listener listener for execution state changes, may be {@code null}
	 * @return the started session
	 * @throws DapManager.DapUnavailableException when the language server cannot start a
	 * session
	 */
	public static DapSession startJava(Path file, LspClient languageServer, List<Integer> breakpoints,
			DapSession.Listener listener) throws DapManager.DapUnavailableException {
		Objects.requireNonNull(file, "file");
		return DapManager.createDefault()
			.startJava(file, languageServer == null ? null : languageServer::executeCommand, breakpoints, listener);
	}

}
