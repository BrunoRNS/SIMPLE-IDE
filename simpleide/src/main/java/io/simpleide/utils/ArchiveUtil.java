package io.simpleide.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

/**
 * Extracts ZIP and TAR archives.
 *
 * <p>
 * The archive format is inferred from the file name, so downloaded files must keep their
 * original extension. Callers own the archive and remain responsible for deleting it
 * after extraction.
 *
 * <p>
 * Covered by {@code DownloadUtilTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 * @author LuisOtavio13
 * @since v1.1.0
 */
public final class ArchiveUtil {

	private ArchiveUtil() {
	}

	/**
	 * Extracts an archive into a directory.
	 * @param archive archive file to extract
	 * @param destination directory that receives the archive contents
	 * @throws IOException when the archive cannot be read or extracted
	 */
	public static void extract(Path archive, Path destination) throws IOException {
		Objects.requireNonNull(archive, "archive");
		Objects.requireNonNull(destination, "destination");
		Archiver archiver = ArchiverFactory.createArchiver(archive.toFile());
		archiver.extract(archive.toFile(), destination.toFile());
	}

}
