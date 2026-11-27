package io.simpleide.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;

/**
 * Strict UTF-8 file reading and recursive deletion helpers.
 *
 * <p>
 * The reader rejects symbolic links and non-regular files, and reports malformed or
 * unmappable input instead of silently replacing characters. Recursive deletion does not
 * follow symbolic links.
 *
 * <p>
 * Covered by {@code ResourceAndFileUtilTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class FileUtil {

	private FileUtil() {
	}

	/**
	 * Reads a regular file as strict UTF-8 text without following symbolic links.
	 * @param path file to read
	 * @return the decoded file content
	 * @throws IllegalArgumentException when {@code path} is {@code null}
	 * @throws IOException when the path is not a regular file or cannot be read or
	 * decoded
	 */
	public static String readString(Path path) throws IOException {
		if (path == null) {
			throw new IllegalArgumentException("Path must not be null.");
		}

		Path normalizedPath = path.toAbsolutePath().normalize();
		BasicFileAttributes attributes = Files.readAttributes(normalizedPath, BasicFileAttributes.class,
				LinkOption.NOFOLLOW_LINKS);
		if (!attributes.isRegularFile()) {
			throw new IOException("Path is not a regular file: " + normalizedPath);
		}

		Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
		try (SeekableByteChannel channel = Files.newByteChannel(normalizedPath, options);
				InputStream input = Channels.newInputStream(channel)) {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(input.readAllBytes()))
				.toString();
		}
	}

	/**
	 * Deletes a file or directory tree without following symbolic links.
	 * @param path file or directory to delete
	 * @throws IOException when an entry cannot be deleted
	 */
	public static void deleteRecursively(Path path) throws IOException {
		Objects.requireNonNull(path, "path");
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
			public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
				if (error != null) {
					throw error;
				}
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

}
