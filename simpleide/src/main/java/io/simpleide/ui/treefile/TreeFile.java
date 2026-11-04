package io.simpleide.ui.treefile;

import java.nio.file.Path;

public abstract class TreeFile {
  private final Path path;
  private final String name;

  protected TreeFile(Path path, String name) {
    this.path = path;
    this.name = name;
  }

  public Path getPath() {
    return path;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
