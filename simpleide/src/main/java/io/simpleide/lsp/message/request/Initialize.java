package io.simpleide.lsp.message.request;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.simpleide.lsp.protocol.JsonRpc;

public final class Initialize extends JsonRpc {

  public Initialize() {
    this(null);
  }

  public Initialize(Path workspaceRoot) {
    super(1, "initialize", createParams(workspaceRoot));
  }

  private static Map<String, Object> createParams(Path workspaceRoot) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("processId", ProcessHandle.current().pid());
    params.put("clientInfo", Map.of("name", "SIMPLE-IDE"));
    params.put("locale", Locale.getDefault().toLanguageTag());
    params.put("rootUri", workspaceRoot == null ? null : workspaceRootUri(workspaceRoot));
    params.put("capabilities", Map.of());
    params.put("trace", "off");
    params.put("workspaceFolders", workspaceRoot == null
        ? null
        : List.of(Map.of(
            "uri", workspaceRootUri(workspaceRoot),
            "name", workspaceName(workspaceRoot))));
    return params;
  }

  private static String workspaceRootUri(Path workspaceRoot) {
    return workspaceRoot.toAbsolutePath().normalize().toUri().toString();
  }

  private static String workspaceName(Path workspaceRoot) {
    Path fileName = workspaceRoot.getFileName();
    return fileName == null ? workspaceRoot.toString() : fileName.toString();
  }
}