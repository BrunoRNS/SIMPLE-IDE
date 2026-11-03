package io.simpleide.ui.filetree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class File {

  private static final Logger LOGGER = LoggerFactory.getLogger(File.class);

  private Path file;
  private boolean isOpen;
  private boolean isDirectory;
  private List<File> children;

  public File(Path file, boolean isDirectory) {
    this.children = new ArrayList<>();
    this.file = file;
    this.isDirectory = isDirectory;
    if (this.isDirectory) {
      loadingChildren();
    }
  }

  public File(Path file) {
    this(file, Files.isDirectory(file));
  }

  private void loadingChildren() {
    try (Stream<Path> paths = Files.list(this.file)) {
      paths.forEach(path -> {
        this.children.add(new File(path));
      });
    } catch (IOException e) {
      LOGGER.error(e.getMessage());
      e.printStackTrace();
    }
  }

  public boolean isDirectory() {
    return this.isDirectory;
  }

  public Path getFile() {
    return this.file;
  }

  public String getName() {
    return this.file.getFileName().toString();
  }

  public void closeFile() {
    isOpen = false;
  }

}
