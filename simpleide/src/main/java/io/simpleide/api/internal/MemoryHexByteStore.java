package io.simpleide.api.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
final class MemoryHexByteStore implements HexByteStore {

	private final byte[] original;

	private final byte[] data;

	private boolean modified;

	MemoryHexByteStore(byte[] content) {
		original = Objects.requireNonNull(content, "content").clone();
		data = original.clone();
	}

	@Override
	public long size() {
		return data.length;
	}

	@Override
	public int getByte(long offset) {
		return data[(int) offset] & 0xff;
	}

	@Override
	public void setByte(long offset, int value) {
		data[(int) offset] = (byte) value;
		modified = !Arrays.equals(data, original);
	}

	@Override
	public boolean isModified() {
		return modified;
	}

	@Override
	public void save(Path path) throws IOException {
		Files.write(path, data);
		System.arraycopy(data, 0, original, 0, data.length);
		modified = false;
	}

	@Override
	public void close() {
	}

}
