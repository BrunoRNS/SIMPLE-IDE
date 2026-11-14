package io.simpleide.lsp.dowload;

import java.util.List;

public class LspRegistry {
  private final List<LspServer> servers;

  public LspRegistry(List<LspServer> servers) {
    this.servers = servers;
  }

  public List<LspServer> getServers() {
    return servers;
  }
}
