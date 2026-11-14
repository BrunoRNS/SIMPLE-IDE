package io.simplebytecode.compiler.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * File reading helpers used by the compiler runners.
 *
 * <p>
 * {@link #readString(Path)} reads a regular file as strictly decoded UTF-8 text without
 * following symbolic links.
 *
 * <p>
 * Covered by {@code FileUtilTest}.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public final class FileUtil {

	private FileUtil() {
	}

	/**
	 * Reads a regular file as UTF-8 text.
	 *
	 * <p>
	 * Malformed and unmappable input is rejected rather than replaced. Symbolic links are
	 * not followed.
	 * @param path the file to read
	 * @return the complete file contents
	 * @throws IllegalArgumentException if {@code path} is {@code null}
	 * @throws IOException if the path is not a regular file or the file cannot be read or
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

}
