package io.simpleide.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResourceAndFileUtilTest {
  @Test
  public void readsUtf8FilesFromPath() throws IOException {
    Path file = Files.createTempFile("simpleide-file-util-", ".txt");
    try {
      Files.writeString(file, "olá, mundo", StandardCharsets.UTF_8);

      assertEquals("olá, mundo", FileUtil.readString(file));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  public void resolvesResourcesByPackageNameOrClass() throws IOException {
    Path fromPackage = ResourceUtil.getResourcePath("io.simpleide.utils", "sample.txt");
    Path fromClass = ResourceUtil.getResourcePath(FileUtil.class, "sample.txt");

    assertEquals("resource content\n", normalizeNewlines(FileUtil.readString(fromPackage)));
    assertEquals("resource content\n", normalizeNewlines(FileUtil.readString(fromClass)));
  }

  private static String normalizeNewlines(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }

  @Test
  public void rejectsResourcePathTraversal() {
    assertThatThrownBy(() -> ResourceUtil.getResourcePath("io.simpleide.utils", "../sample.txt"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("..");
  }
}
