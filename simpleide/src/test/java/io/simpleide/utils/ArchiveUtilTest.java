package io.simpleide.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link ArchiveUtil}.
 */
class ArchiveUtilTest {

	@TempDir
	Path directory;

	@Test
	void extractsAnArchiveIntoTheDestination() throws IOException {
		Path archive = directory.resolve("bundle.zip");
		try (OutputStream output = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(output)) {
			zip.putNextEntry(new ZipEntry("nested/file.txt"));
			zip.write("content".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		Path destination = Files.createDirectory(directory.resolve("destination"));

		ArchiveUtil.extract(archive, destination);

		assertThat(destination.resolve("nested/file.txt")).hasContent("content");
	}

}
