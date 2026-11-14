package io.simplebytecode.compiler.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilTest {

  @TempDir Path tempDirectory;

  @Test
  void readsUtf8Source() throws Exception {
    Path source = tempDirectory.resolve("source.txt");
    Files.writeString(source, "olá, mundo");

    assertThat(FileUtil.readString(source)).isEqualTo("olá, mundo");
  }

  @Test
  void rejectsNullNonFileAndInvalidUtf8Paths() throws Exception {
    assertThatThrownBy(() -> FileUtil.readString(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FileUtil.readString(tempDirectory))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("not a regular file");

    Path invalidUtf8 = tempDirectory.resolve("invalid.txt");
    Files.write(invalidUtf8, new byte[] {(byte) 0xc3, (byte) 0x28});
    assertThatThrownBy(() -> FileUtil.readString(invalidUtf8)).isInstanceOf(IOException.class);
  }
}
