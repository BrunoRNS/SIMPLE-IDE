package io.simplebytecode.compiler;

import io.simplebytecode.compiler.utils.FileUtil;
import java.nio.file.Path;

public interface RunnerInterface extends Runnable {

  void prepare(String src);

  default String loadFile(Path file) {
    try {
      return FileUtil.readString(file);
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Failed to read file: " + file, e);
    }
  }
}
