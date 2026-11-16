package io.simpleide.ui.treefile;

import java.nio.file.Path;

/**
 * Common base for entries in the project file tree.
 *
 * <p>
 * Every entry has a path and a display name. The subclasses are {@link Directory} and
 * {@link File}.
 *
 * <p>
 * Covered by {@code TreeFileTest}.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public abstract class TreeFile {

	private final Path path;

	private final String name;

	protected TreeFile(Path path, String name) {
		this.path = path;
		this.name = name;
	}

	/**
	 * Returns the file system path of this entry.
	 * @return the entry path
	 */
	public Path getPath() {
		return path;
	}

	/**
	 * Returns the display name of this entry.
	 * @return the entry name
	 */
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}

}
