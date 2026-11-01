package io.simpleide.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ResourceAndFileUtilTest {
    @Test
    void readsUtf8FilesFromPath() throws IOException {
        Path file = Files.createTempFile("simpleide-file-util-", ".txt");
        try {
            Files.writeString(file, "olá, mundo", StandardCharsets.UTF_8);

            assertEquals("olá, mundo", FileUtil.readString(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void resolvesResourcesByPackageNameOrClass() throws IOException {
        Path fromPackage = ResourceUtil.getResourcePath("io.simpleide.utils", "sample.txt");
        Path fromClass = ResourceUtil.getResourcePath(FileUtil.class, "sample.txt");

        assertEquals("resource content\n", FileUtil.readString(fromPackage));
        assertEquals("resource content\n", FileUtil.readString(fromClass));
    }

    @Test
    void rejectsResourcePathTraversal() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ResourceUtil.getResourcePath("io.simpleide.utils", "../sample.txt")
        );
    }
}
