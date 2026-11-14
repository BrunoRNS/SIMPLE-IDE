package io.simpleide.language.exceptions;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class ProcessFatalException extends UncheckedIOException {

  private final Process processCause;

  public ProcessFatalException(String message, IOException cause) {
    super(message, cause);
    this.processCause = null;
  }

  public ProcessFatalException(String message, Process pcause) {
    super(message, new IOException(message));
    this.processCause = pcause;
  }

  @Override
  public IOException getCause() {
    return (IOException) super.getCause();
  }

  public Process getProcessCause() {
    return this.processCause;
  }
}
