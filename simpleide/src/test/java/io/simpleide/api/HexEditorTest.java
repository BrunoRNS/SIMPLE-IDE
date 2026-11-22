package io.simpleide.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests bounded file-backed HEX editing.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
class HexEditorTest {

	@Test
	void editsAndSavesLargeFilesUsingBoundedChunks() throws Exception {
		Path path = Files.createTempFile("simpleide-large-hex-", ".bin");
		long size = 128L * 1024 * 1024;
		try {
			try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
				channel.position(size - 1);
				channel.write(ByteBuffer.wrap(new byte[] { 0x41 }));
			}
			try (HexEditor editor = new HexEditor(path)) {
				assertThat(editor.byteCount()).isEqualTo(size);
				assertThat(editor.getByte(0)).isZero();
				assertThat(editor.getByte(size - 1)).isEqualTo(0x41);
				editor.setByte(size - 1, 0x42);
				editor.setByte(5L * 64 * 1024 + 3, 0x43);
				assertThat(editor.isModified()).isTrue();
				editor.save(path);
				assertThat(editor.isModified()).isFalse();
			}
			try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
				ByteBuffer last = ByteBuffer.allocate(1);
				channel.read(last, size - 1);
				assertThat(last.array()[0] & 0xff).isEqualTo(0x42);
				ByteBuffer edited = ByteBuffer.allocate(1);
				channel.read(edited, 5L * 64 * 1024 + 3);
				assertThat(edited.array()[0] & 0xff).isEqualTo(0x43);
			}
		}
		finally {
			Files.deleteIfExists(path);
		}
	}

	@Test
	void refusesToMaterializeAnOversizedByteArray() throws IOException {
		Path path = Files.createTempFile("simpleide-large-hex-array-", ".bin");
		try {
			try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
				channel.position(16L * 1024 * 1024);
				channel.write(ByteBuffer.wrap(new byte[] { 0 }));
			}
			try (HexEditor editor = new HexEditor(path)) {
				assertThatThrownBy(editor::bytes).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("too large");
			}
		}
		finally {
			Files.deleteIfExists(path);
		}
	}

}
