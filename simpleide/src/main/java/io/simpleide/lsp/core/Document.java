package io.simpleide.lsp.core;

import java.nio.file.Path;

public class Document {
  private final Path path;
  private final String uri;
  private final String languageId;

  private int version;
  private boolean opened;
  private boolean modified;

  public Document(Path path, String languageId) {
    this.path = path.toAbsolutePath().normalize();
    this.uri = this.path.toUri().toString();
    this.languageId = languageId;

    this.version = 1;
    this.opened = true;
    this.modified = false;
  }

  public Path getPath() {
    return path;
  }

  public String getUri() {
    return uri;
  }

  public String getLanguageId() {
    return languageId;
  }

  public int getVersion() {
    return version;
  }

  public void incrementVersion() {
    version++;
  }

  public boolean isOpened() {
    return opened;
  }

  public void setOpened(boolean opened) {
    this.opened = opened;
  }

  public boolean isModified() {
    return modified;
  }

  public void setModified(boolean modified) {
    this.modified = modified;
  }
}
