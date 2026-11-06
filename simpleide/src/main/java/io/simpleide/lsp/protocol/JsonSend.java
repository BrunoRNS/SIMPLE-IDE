package io.simpleide.lsp.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

public final class JsonSend {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonSend() {
  }

  public static String send(JsonRpc jsonRpc) {
    Objects.requireNonNull(jsonRpc, "jsonRpc");
    try {
      String json = MAPPER.writeValueAsString(jsonRpc);
      int contentLength = json.getBytes(StandardCharsets.UTF_8).length;
      return "Content-Length: " + contentLength + "\r\n\r\n" + json;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize JSON-RPC message.", exception);
    }
  }
}
