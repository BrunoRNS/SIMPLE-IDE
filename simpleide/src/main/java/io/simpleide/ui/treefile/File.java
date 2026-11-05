package io.simpleide.ui.treefile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.utils.FileUtil;

public class File extends TreeFile {
  private static final Logger LOGGER = LoggerFactory.getLogger(File.class);

  private final TypeFile typeFile;
  private final StringBuffer code;

  public File(Path path, String name, TypeFile typeFile, StringBuffer code) {
    super(path, name);
    this.typeFile = typeFile;
    this.code = code;
  }

  public File(Path path) throws IOException {
    this(path, loadNameFile(path), getTypeFile(path), new StringBuffer(Files.readString(path)));
  }

  public TypeFile getTypeFile() {
    return typeFile;
  }

  public StringBuffer getCode() {
    return code;
  }

  private static String loadNameFile(Path path) {
    return path.getFileName().toString();
  }

  private static TypeFile getTypeFile(Path path) {
    String name = loadNameFile(path);
    String ext = name.substring(name.lastIndexOf('.') + 1);
    TypeFile typeFile;
    switch (ext) {
      case "java" -> typeFile = TypeFile.JAVA;
      case "kts" -> typeFile = TypeFile.KOTLIN;
      case "groovy" -> typeFile = TypeFile.GROOVY;
      default -> typeFile = TypeFile.TXT;
    }
    LOGGER.info("Load file by extension {}", ext);
    return typeFile;
  }
}
