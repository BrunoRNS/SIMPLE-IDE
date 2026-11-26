package io.simpleide.api.internal;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Internal storage and table-model support for the public {@code HexEditor} facade.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public interface HexByteStore extends AutoCloseable {

	long size();

	int getByte(long offset) throws IOException;

	void setByte(long offset, int value) throws IOException;

	boolean isModified();

	void save(Path path) throws IOException;

	@Override
	void close() throws IOException;

}
