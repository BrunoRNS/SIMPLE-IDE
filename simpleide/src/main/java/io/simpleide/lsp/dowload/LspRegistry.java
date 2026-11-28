package io.simpleide.lsp.dowload;

import java.util.List;

/**
 * List of language server definitions loaded from {@code registry.json}.
 *
 * <p>
 * The registry is the in-memory view of the bundled JSON resource used by
 * {@link LspManager} to select, install and launch servers.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class LspRegistry {

	private final List<LspServer> servers;

	/**
	 * Creates a registry backed by the given server definitions.
	 * @param servers server definitions
	 */
	public LspRegistry(List<LspServer> servers) {
		this.servers = servers;
	}

	/**
	 * Returns the server definitions in registry order.
	 * @return the server definitions
	 */
	public List<LspServer> getServers() {
		return servers;
	}

}
