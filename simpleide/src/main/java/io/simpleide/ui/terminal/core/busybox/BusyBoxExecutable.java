package io.simpleide.ui.terminal.core.busybox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BusyBoxExecutable implements AutoCloseable {

  private final Path directory;
  private final Path path;

  private BusyBoxExecutable(Path directory, Path path) {
    this.directory = directory;
    this.path = path;
  }

  static BusyBoxExecutable create(String fileName) throws IOException {
    Path directory = Files.createTempDirectory("simpleide-busybox-");
    return new BusyBoxExecutable(directory, directory.resolve(fileName));
  }

  void copyFrom(Class<?> resourceOwner, String resourcePath) throws IOException {
    try (InputStream input =
        resourceOwner.getResourceAsStream(
            "/io/simpleide/ui/terminal/core/busybox/" + resourcePath)) {
      if (input == null) {
        throw new IOException("Embedded BusyBox resource not found: " + resourcePath);
      }
      Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  void makeExecutable() throws IOException {
    if (!path.toFile().setExecutable(true, true) && !Files.isExecutable(path)) {
      throw new IOException("Unable to make embedded BusyBox executable: " + path);
    }
  }

  public Path getPath() {
    return path;
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      failure = exception;
    }
    try {
      Files.deleteIfExists(directory);
    } catch (IOException exception) {
      if (failure == null) {
        failure = exception;
      } else {
        failure.addSuppressed(exception);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
