package io.simpleide.lsp;

/**
 * Immutable status snapshot for language server installation and startup.
 *
 * <p>
 * The status is delivered to UI listeners while a server is checked, downloaded,
 * installed or started. The {@link State} enum describes the current phase, and the
 * {@code id} identifies the server instance so updates for different languages or
 * workspace roots can be told apart.
 *
 * @param id identifier of the server instance, usually language id and workspace root
 * @param serverName human-readable name of the language server
 * @param state current phase of the installation or startup sequence
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public record LspStatus(String id, String serverName, State state) {

	/**
	 * Phases reported while a language server is prepared and started.
	 *
	 * @author LuisOtavio13
	 * @since v1.0.0beta
	 */
	public enum State {

		CHECKING_VERSION, DOWNLOADING, EXTRACTING, INSTALLING, STARTING, RUNNING, FAILED, IDLE

	}
}
