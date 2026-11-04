package io.simpleide.ui.treefile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class TreeFileFactory {

  public static Tree createDirectory() {
    try {
      return createDirectory(Path.of(System.getProperty("user.dir")));
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to load the current project directory.", e);
    }
  }

  public static Directory loadRootDirectory() throws IOException {
    return new Directory(Path.of(System.getProperty("user.dir")));
  }

  public static Tree createDirectory(Path path) throws IOException {
    return new Tree(new Directory(path));
  }
}
