package io.simpleide.utils.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TreeFile;

public class FileService {

  public void deleteFile(Path path) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }

    Files.walkFileTree(path, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
        if (exception != null) {
          throw exception;
        }
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public Path renameFile(Path oldPath, String name) throws IOException {
    Path namePath = parseName(name);
    Path newPath = oldPath.getParent().resolve(namePath);
    Files.move(oldPath, newPath);
    return newPath;
  }

  public TreeFile createNewFile(Directory parent, String name, boolean isDirectory) throws IOException {
    Path path = parent.getPath().resolve(parseName(name));
    if (isDirectory) {
      Files.createDirectory(path);
      return new Directory(path);
    }

    Files.createFile(path);
    return new File(path);
  }

  private Path parseName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Enter a valid file or directory name.");
    }

    Path namePath = Path.of(name.strip());
    if (namePath.isAbsolute() || namePath.getNameCount() != 1
        || namePath.toString().equals(".") || namePath.toString().equals("..")) {
      throw new IllegalArgumentException("Enter a valid file or directory name.");
    }
    return namePath;
  }
}
