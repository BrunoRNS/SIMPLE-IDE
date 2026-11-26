package io.simpleide.api.internal;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Creates internal storage implementations for {@code HexEditor}.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class HexByteStores {

	private HexByteStores() {
	}

	public static HexByteStore memory(byte[] content) {
		return new MemoryHexByteStore(content);
	}

	public static HexByteStore file(Path path) throws IOException {
		return new FileHexByteStore(path);
	}

}
