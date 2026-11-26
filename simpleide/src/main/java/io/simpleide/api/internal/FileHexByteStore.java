package io.simpleide.api.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class FileHexByteStore implements HexByteStore {

	private static final int CHUNK_SIZE = 64 * 1024;

	private static final int MAX_CACHED_CHUNKS = 4;

	private Path source;

	private FileChannel sourceChannel;

	private final long size;

	private final LinkedHashMap<Long, HexChunk> chunks = new LinkedHashMap<>(8, 0.75f, true);

	private Path overlayPath;

	private Path dirtyPath;

	private FileChannel overlayChannel;

	private FileChannel dirtyChannel;

	private boolean modified;

	FileHexByteStore(Path path) throws IOException {
		source = path.toAbsolutePath().normalize();
		size = Files.size(source);
		sourceChannel = FileChannel.open(source, StandardOpenOption.READ);
	}

	@Override
	public long size() {
		return size;
	}

	@Override
	public synchronized int getByte(long offset) throws IOException {
		HexChunk chunk = chunk(offset / CHUNK_SIZE);
		return chunk.data[(int) (offset % CHUNK_SIZE)] & 0xff;
	}

	@Override
	public synchronized void setByte(long offset, int value) throws IOException {
		HexChunk chunk = chunk(offset / CHUNK_SIZE);
		chunk.data[(int) (offset % CHUNK_SIZE)] = (byte) value;
		chunk.dirty = true;
		modified = true;
	}

	@Override
	public synchronized boolean isModified() {
		return modified;
	}

	@Override
	public synchronized void save(Path targetPath) throws IOException {
		Path target = targetPath.toAbsolutePath().normalize();
		for (HexChunk chunk : chunks.values()) {
			flush(chunk);
		}
		if (!target.equals(source)) {
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
		if (dirtyChannel != null) {
			try (FileChannel destination = FileChannel.open(target, StandardOpenOption.WRITE)) {
				long pageCount = size == 0 ? 0 : 1 + (size - 1) / CHUNK_SIZE;
				copyDirtyPages(destination, pageCount);
			}
		}
		if (sourceChannel != null) {
			sourceChannel.close();
		}
		source = target;
		sourceChannel = FileChannel.open(source, StandardOpenOption.READ);
		clearOverlay();
		chunks.clear();
		modified = false;
	}

	@Override
	public synchronized void close() throws IOException {
		if (sourceChannel != null) {
			sourceChannel.close();
		}
		clearOverlay();
		chunks.clear();
	}

	private HexChunk chunk(long page) throws IOException {
		HexChunk chunk = chunks.get(page);
		if (chunk == null) {
			long start = page * CHUNK_SIZE;
			int length = (int) Math.min(CHUNK_SIZE, size - start);
			byte[] data = new byte[length];
			ByteBuffer buffer = ByteBuffer.wrap(data);
			FileChannel channel = isDirty(page) ? overlayChannel : sourceChannel;
			if (channel != null) {
				readFully(channel, buffer, start);
			}
			chunk = new HexChunk(page, data);
			chunks.put(page, chunk);
			if (chunks.size() > MAX_CACHED_CHUNKS) {
				Iterator<Map.Entry<Long, HexChunk>> iterator = chunks.entrySet().iterator();
				HexChunk eldest = iterator.next().getValue();
				flush(eldest);
				iterator.remove();
			}
		}
		return chunk;
	}

	private void flush(HexChunk chunk) throws IOException {
		if (!chunk.dirty) {
			return;
		}
		ensureOverlay();
		long start = chunk.page * CHUNK_SIZE;
		writeFully(overlayChannel, ByteBuffer.wrap(chunk.data), start);
		markDirty(chunk.page);
		chunk.dirty = false;
	}

	private boolean isDirty(long page) throws IOException {
		if (dirtyChannel == null) {
			return false;
		}
		long markerOffset = page / 8;
		ByteBuffer marker = ByteBuffer.allocate(1);
		readFully(dirtyChannel, marker, markerOffset);
		return (marker.array()[0] & (1 << (page % 8))) != 0;
	}

	private void markDirty(long page) throws IOException {
		long markerOffset = page / 8;
		ByteBuffer marker = ByteBuffer.allocate(1);
		readFully(dirtyChannel, marker, markerOffset);
		byte value = (byte) (marker.array()[0] | (1 << (page % 8)));
		writeFully(dirtyChannel, ByteBuffer.wrap(new byte[] { value }), markerOffset);
	}

	private void copyDirtyPages(FileChannel destination, long pageCount) throws IOException {
		ByteBuffer markers = ByteBuffer.allocate(8192);
		ByteBuffer pageData = ByteBuffer.allocate(CHUNK_SIZE);
		long markerCount = pageCount == 0 ? 0 : 1 + (pageCount - 1) / 8;
		for (long markerOffset = 0; markerOffset < markerCount;) {
			int markerLength = (int) Math.min(markers.capacity(), markerCount - markerOffset);
			markers.clear();
			markers.limit(markerLength);
			readFully(dirtyChannel, markers, markerOffset);
			for (int index = 0; index < markerLength; index++) {
				int dirtyBits = markers.array()[index] & 0xff;
				while (dirtyBits != 0) {
					int bit = Integer.numberOfTrailingZeros(dirtyBits);
					long page = (markerOffset + index) * 8 + bit;
					long start = page * CHUNK_SIZE;
					int length = (int) Math.min(CHUNK_SIZE, size - start);
					pageData.clear();
					pageData.limit(length);
					readFully(overlayChannel, pageData, start);
					pageData.flip();
					writeFully(destination, pageData, start);
					dirtyBits &= dirtyBits - 1;
				}
			}
			markerOffset += markerLength;
		}
	}

	private void ensureOverlay() throws IOException {
		if (overlayChannel != null) {
			return;
		}
		overlayPath = Files.createTempFile("simpleide-hex-edits-", ".bin");
		dirtyPath = Files.createTempFile("simpleide-hex-dirty-", ".map");
		overlayChannel = FileChannel.open(overlayPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
		dirtyChannel = FileChannel.open(dirtyPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
	}

	private void clearOverlay() throws IOException {
		if (overlayChannel != null) {
			overlayChannel.close();
			dirtyChannel.close();
			overlayChannel = null;
			dirtyChannel = null;
			Files.deleteIfExists(overlayPath);
			Files.deleteIfExists(dirtyPath);
			overlayPath = null;
			dirtyPath = null;
		}
	}

	private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
		while (buffer.hasRemaining()) {
			int read = channel.read(buffer, position + buffer.position());
			if (read < 0) {
				break;
			}
			if (read == 0) {
				Thread.onSpinWait();
			}
		}
	}

	private static void writeFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
		while (buffer.hasRemaining()) {
			int written = channel.write(buffer, position + buffer.position());
			if (written == 0) {
				Thread.onSpinWait();
			}
		}
	}

	private static final class HexChunk {

		private final long page;

		private final byte[] data;

		private boolean dirty;

		HexChunk(long page, byte[] data) {
			this.page = page;
			this.data = data;
		}

	}

}
