package io.simpleide.lsp.protocol;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpc {
  private final String jsonrpc = "2.0";
  private final Object id;
  private final String method;
  private final Object params;

  public JsonRpc(String method) {
    this(1, method, null);
  }

  public JsonRpc(Object id, String method, Object params) {
    this.id = Objects.requireNonNull(id, "id");
    this.method = Objects.requireNonNull(method, "method");
    this.params = params;
  }

  public String getJsonrpc() {
    return jsonrpc;
  }

  public Object getId() {
    return id;
  }

  public String getMethod() {
    return method;
  }

  public Object getParams() {
    return params;
  }
}
