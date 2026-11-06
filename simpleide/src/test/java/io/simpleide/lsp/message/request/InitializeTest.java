package io.simpleide.lsp.message.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.simpleide.lsp.protocol.JsonSend;

class InitializeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesInitializeRequestWithWorkspaceAndUtf8ContentLength() throws Exception {
    Path workspace = Path.of("projeto-ação");
    String message = JsonSend.send(new Initialize(workspace));
    String[] parts = message.split("\\r\\n\\r\\n", 2);
    assertThat(parts).hasSize(2);
    JsonNode request = objectMapper.readTree(parts[1]);
    JsonNode params = request.path("params");
    String workspaceUri = workspace.toAbsolutePath().normalize().toUri().toString();

    assertThat(parts[0]).startsWith("Content-Length: ");
    assertThat(Integer.parseInt(parts[0].substring("Content-Length: ".length())))
        .isEqualTo(parts[1].getBytes(StandardCharsets.UTF_8).length);
    assertThat(request.path("jsonrpc").asText()).isEqualTo("2.0");
    assertThat(request.path("id").asInt()).isEqualTo(1);
    assertThat(request.path("method").asText()).isEqualTo("initialize");
    assertThat(params.path("processId").asLong()).isEqualTo(ProcessHandle.current().pid());
    assertThat(params.path("rootUri").asText()).isEqualTo(workspaceUri);
    assertThat(params.path("capabilities").isObject()).isTrue();
    assertThat(params.path("workspaceFolders")).hasSize(1);
    assertThat(params.path("workspaceFolders").get(0).path("uri").asText()).isEqualTo(workspaceUri);
    assertThat(params.path("workspaceFolders").get(0).path("name").asText()).isEqualTo("projeto-ação");
  }

  @Test
  void permitsInitializationWithoutAWorkspace() throws Exception {
    JsonNode params = objectMapper.readTree(jsonBody(JsonSend.send(new Initialize())))
        .path("params");

    assertThat(params.path("rootUri").isNull()).isTrue();
    assertThat(params.path("workspaceFolders").isNull()).isTrue();
    assertThat(params.path("capabilities").isObject()).isTrue();
  }

  private String jsonBody(String message) {
    return message.split("\\r\\n\\r\\n", 2)[1];
  }
}
