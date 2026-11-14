package io.simpleide.lsp.client;

import java.io.IOException;

public final class LspServerStartupException extends IOException {
  private static final long serialVersionUID = 1L;

  private final int exitCode;

  public LspServerStartupException(int exitCode) {
    super(createMessage(exitCode));
    this.exitCode = exitCode;
  }

  public int getExitCode() {
    return exitCode;
  }

  public boolean isJvmCompatibilityFailure() {
    return exitCode == 13;
  }

  private static String createMessage(int exitCode) {
    String message = "Language server exited before initialization (code " + exitCode + ").";
    if (exitCode == 13) {
      message +=
          " The Java runtime used to launch it is probably older than the server requires."
              + " On institutional machines with a fixed Java runtime this is a known issue.";
    }
    return message;
  }
}
